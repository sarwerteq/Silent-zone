
import express from "express";
import { pool, query } from "./db.js";
import { authenticate, requireRoles } from "./auth.js";

const router = express.Router();

const ADMIN_ROLES = ["AREA_ADMIN", "DISTRICT_ADMIN", "SUPER_ADMIN"];
const CATEGORIES = ["MOSQUE", "SCHOOL", "HOSPITAL", "LIBRARY", "OTHER"];
const ACTIONS = ["SILENT", "VIBRATE", "DND"];

async function canManageArea(user, areaId) {
  if (user.role === "SUPER_ADMIN") return true;

  const result = await query(
    `SELECT 1
     FROM admin_area_assignments aa
     JOIN admin_areas a ON a.id = aa.area_id
     WHERE aa.user_id = $1
       AND aa.area_id = $2
       AND a.is_active = TRUE`,
    [user.id, areaId]
  );

  return result.rowCount > 0;
}

function validateZone(body, partial = false) {
  const errors = [];

  if (!partial || body.name !== undefined) {
    if (typeof body.name !== "string" ||
        body.name.trim().length < 2 ||
        body.name.trim().length > 120) {
      errors.push("name must be 2–120 characters");
    }
  }

  if (!partial || body.category !== undefined) {
    if (!CATEGORIES.includes(body.category)) {
      errors.push("Invalid category");
    }
  }

  for (const field of ["latitude", "longitude"]) {
    if (!partial || body[field] !== undefined) {
      const value = body[field];
      if (typeof value !== "number" || !Number.isFinite(value)) {
        errors.push(`${field} must be a number`);
      } else if (
        (field === "latitude" && (value < -90 || value > 90)) ||
        (field === "longitude" && (value < -180 || value > 180))
      ) {
        errors.push(`${field} is out of range`);
      }
    }
  }

  if (!partial || body.radius_m !== undefined) {
    if (!Number.isInteger(body.radius_m) ||
        body.radius_m < 50 ||
        body.radius_m > 1000) {
      errors.push("radius_m must be between 50 and 1000");
    }
  }

  if (!partial || body.sound_action !== undefined) {
    if (!ACTIONS.includes(body.sound_action)) {
      errors.push("Invalid sound_action");
    }
  }

  if (body.schedule_enabled !== undefined &&
      typeof body.schedule_enabled !== "boolean") {
    errors.push("schedule_enabled must be a boolean");
  }

  if (body.schedule_enabled === true) {
    if (
      typeof body.schedule_start !== "string" ||
      !/^\d{2}:\d{2}$/.test(body.schedule_start) ||
      typeof body.schedule_end !== "string" ||
      !/^\d{2}:\d{2}$/.test(body.schedule_end)
    ) {
      errors.push("A valid schedule_start and schedule_end are required (HH:MM)");
    }
  }

  if (body.timezone !== undefined) {
    if (typeof body.timezone !== "string" ||
        body.timezone.length > 80) {
      errors.push("Invalid timezone");
    }
  }

  return errors;
}

// Mobile app downloads active zones. Sync version is included.
router.get("/", async (_req, res) => {
  try {
    const result = await query(
      `SELECT id, area_id, name, category, latitude, longitude,
              radius_meters AS radius_m, sound_action, schedule_enabled,
              schedule_start, schedule_end, timezone, version,
              updated_at
       FROM silent_zones
       WHERE is_active = TRUE
       ORDER BY updated_at DESC`
    );

    res.json({
      success: true,
      zones: result.rows,
      server_time: new Date().toISOString()
    });
  } catch (error) {
    console.error("Zone list failed:", error.message);
    res.status(500).json({
      success: false,
      message: "Could not load zones"
    });
  }
});

router.post(
  "/",
  authenticate,
  requireRoles(...ADMIN_ROLES),
  async (req, res) => {
    const client = await pool.connect();

    try {
      const body = req.body || {};
      const errors = validateZone(body);

      if (!errors.length && typeof body.area_id !== "string") {
        errors.push("area_id is required");
      }

      if (errors.length) {
        return res.status(400).json({
          success: false,
          message: "Invalid zone data",
          errors
        });
      }

      if (!(await canManageArea(req.user, body.area_id))) {
        return res.status(403).json({
          success: false,
          message: "You cannot manage this area"
        });
      }

      await client.query("BEGIN");

      const created = await client.query(
        `INSERT INTO silent_zones
          (area_id, name, category, latitude, longitude,radius_meters AS radius_m,
           sound_action, schedule_enabled, schedule_start, schedule_end,
           timezone, created_by, updated_by)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$12)
         RETURNING *`,
        [
          body.area_id,
          body.name.trim(),
          body.category,
          body.latitude,
          body.longitude,
          body.radius_m,
          body.sound_action,
          body.schedule_enabled ?? false,
          body.schedule_start ?? null,
          body.schedule_end ?? null,
          body.timezone ?? "Asia/Kolkata",
          req.user.id
        ]
      );

      const zone = created.rows[0];

      await client.query(
        `INSERT INTO zone_audit_logs
          (zone_id, actor_user_id, action, new_data)
         VALUES ($1,$2,'CREATE',$3::jsonb)`,
        [zone.id, req.user.id, JSON.stringify(zone)]
      );

      await client.query("COMMIT");

      return res.status(201).json({ success: true, zone });
    } catch (error) {
      await client.query("ROLLBACK");
      console.error("Zone creation failed:", error.message);
      return res.status(500).json({
        success: false,
        message: "Could not create zone"
      });
    } finally {
      client.release();
    }
  }
);

router.patch(
  "/:id",
  authenticate,
  requireRoles(...ADMIN_ROLES),
  async (req, res) => {
    const client = await pool.connect();

    try {
      const body = req.body || {};
      const allowedFields = [
        "name", "category", "latitude", "longitude", "radius_m",
        "sound_action", "schedule_enabled", "schedule_start",
        "schedule_end", "timezone", "is_active"
      ];

      const keys = Object.keys(body);

      if (
        keys.length === 0 ||
        keys.some((key) => !allowedFields.includes(key))
      ) {
        return res.status(400).json({
          success: false,
          message: "Provide only supported zone fields"
        });
      }

      const errors = validateZone(body, true);

      if (body.is_active !== undefined &&
          typeof body.is_active !== "boolean") {
        errors.push("is_active must be a boolean");
      }

      if (errors.length) {
        return res.status(400).json({
          success: false,
          message: "Invalid zone data",
          errors
        });
      }

      await client.query("BEGIN");

      const found = await client.query(
        "SELECT * FROM silent_zones WHERE id = $1 FOR UPDATE",
        [req.params.id]
      );

      const oldZone = found.rows[0];

      if (!oldZone) {
        await client.query("ROLLBACK");
        return res.status(404).json({
          success: false,
          message: "Zone not found"
        });
      }

      if (!(await canManageArea(req.user, oldZone.area_id))) {
        await client.query("ROLLBACK");
        return res.status(403).json({
          success: false,
          message: "You cannot manage this zone"
        });
      }

      const fields = {
        name: "name",
        category: "category",
        latitude: "latitude",
        longitude: "longitude",
        radius_m: "radius_m",
        sound_action: "sound_action",
        schedule_enabled: "schedule_enabled",
        schedule_start: "schedule_start",
        schedule_end: "schedule_end",
        timezone: "timezone",
        is_active: "is_active"
      };

      const values = [];
      const sets = keys.map((key) => {
        values.push(key === "name" ? body[key].trim() : body[key]);
        return `${fields[key]} = $${values.length}`;
      });

      values.push(req.user.id);
      sets.push(`updated_by = $${values.length}`);
      sets.push("updated_at = NOW()");
      sets.push("version = version + 1");

      values.push(req.params.id);

      const updated = await client.query(
        `UPDATE silent_zones
         SET ${sets.join(", ")}
         WHERE id = $${values.length}
         RETURNING *`,
        values
      );

      await client.query(
        `INSERT INTO zone_audit_logs
          (zone_id, actor_user_id, action, old_data, new_data)
         VALUES ($1,$2,'UPDATE',$3::jsonb,$4::jsonb)`,
        [
          oldZone.id,
          req.user.id,
          JSON.stringify(oldZone),
          JSON.stringify(updated.rows[0])
        ]
      );

      await client.query("COMMIT");

      return res.json({
        success: true,
        zone: updated.rows[0],
        message: "Zone updated. Connected devices will sync on their next sync."
      });
    } catch (error) {
      await client.query("ROLLBACK");
      console.error("Zone update failed:", error.message);
      return res.status(500).json({
        success: false,
        message: "Could not update zone"
      });
    } finally {
      client.release();
    }
  }
);

export default router;
