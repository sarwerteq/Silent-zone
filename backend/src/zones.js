import express from "express";
import { pool, query } from "./db.js";
import { authenticate, requireRoles } from "./auth.js";

const router = express.Router();

const ADMIN_ROLES = ["AREA_ADMIN", "DISTRICT_ADMIN", "SUPER_ADMIN"];
const CATEGORIES = ["MOSQUE", "SCHOOL", "HOSPITAL", "LIBRARY", "OTHER"];
const ACTIONS = ["SILENT", "VIBRATE", "DND"];
const ALL_DAYS = [1, 2, 3, 4, 5, 6, 7];

/*
  Day mapping:
  1 = Monday
  2 = Tuesday
  3 = Wednesday
  4 = Thursday
  5 = Friday
  6 = Saturday
  7 = Sunday
*/

async function canManageArea(user, areaId, db = null) {
  if (user.role === "SUPER_ADMIN") {
    return true;
  }

  const runQuery = db ? db.query.bind(db) : query;

  const result = await runQuery(
    `SELECT 1
     FROM admin_area_assignments aa
     JOIN admin_areas a
       ON a.id = aa.area_id
     WHERE aa.user_id = $1
       AND aa.area_id = $2
       AND a.is_active = TRUE`,
    [user.id, areaId]
  );

  return result.rowCount > 0;
}

function validTime(value) {
  return (
    typeof value === "string" &&
    /^\d{2}:\d{2}(:\d{2})?$/.test(value)
  );
}

function normaliseDays(days) {
  if (!Array.isArray(days)) {
    return null;
  }

  const values = [...new Set(days.map(Number))]
    .sort((a, b) => a - b);

  if (
    !values.length ||
    values.some(
      (day) =>
        !Number.isInteger(day) ||
        day < 1 ||
        day > 7
    )
  ) {
    return null;
  }

  return values;
}

function validateSchedules(schedules) {
  const errors = [];

  if (!Array.isArray(schedules)) {
    return ["schedules must be an array"];
  }

  if (schedules.length > 30) {
    return ["A zone can have a maximum of 30 schedules"];
  }

  schedules.forEach((schedule, index) => {
    const number = index + 1;

    if (!schedule || typeof schedule !== "object") {
      errors.push(`Schedule ${number} is invalid`);
      return;
    }

    if (
      typeof schedule.name !== "string" ||
      schedule.name.trim().length < 1 ||
      schedule.name.trim().length > 100
    ) {
      errors.push(
        `Schedule ${number} name must be 1–100 characters`
      );
    }

    if (!validTime(schedule.start_time)) {
      errors.push(
        `Schedule ${number} start_time must be HH:MM`
      );
    }

    if (!validTime(schedule.end_time)) {
      errors.push(
        `Schedule ${number} end_time must be HH:MM`
      );
    }

    if (!normaliseDays(schedule.days)) {
      errors.push(
        `Schedule ${number} must contain at least one valid day (1=Mon ... 7=Sun)`
      );
    }

    if (
      schedule.is_active !== undefined &&
      typeof schedule.is_active !== "boolean"
    ) {
      errors.push(
        `Schedule ${number} is_active must be boolean`
      );
    }
  });

  return errors;
}

function validateZone(body, partial = false) {
  const errors = [];

  if (!partial || body.name !== undefined) {
    if (
      typeof body.name !== "string" ||
      body.name.trim().length < 2 ||
      body.name.trim().length > 120
    ) {
      errors.push(
        "name must be 2–120 characters"
      );
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

      if (
        typeof value !== "number" ||
        !Number.isFinite(value)
      ) {
        errors.push(`${field} must be a number`);
      } else if (
        (field === "latitude" &&
          (value < -90 || value > 90)) ||
        (field === "longitude" &&
          (value < -180 || value > 180))
      ) {
        errors.push(`${field} is out of range`);
      }
    }
  }

  if (!partial || body.radius_m !== undefined) {
    if (
      !Number.isInteger(body.radius_m) ||
      body.radius_m < 10 ||
      body.radius_m > 500
    ) {
      errors.push(
        "radius_m must be between 10 and 500"
      );
    }
  }

  if (!partial || body.sound_action !== undefined) {
    if (!ACTIONS.includes(body.sound_action)) {
      errors.push("Invalid sound_action");
    }
  }

  if (
    body.schedule_enabled !== undefined &&
    typeof body.schedule_enabled !== "boolean"
  ) {
    errors.push(
      "schedule_enabled must be a boolean"
    );
  }

  if (body.schedules !== undefined) {
    errors.push(
      ...validateSchedules(body.schedules)
    );
  }

  if (
    body.schedule_enabled === true &&
    body.schedules === undefined
  ) {
    if (
      typeof body.schedule_start !== "string" ||
      !validTime(body.schedule_start) ||
      typeof body.schedule_end !== "string" ||
      !validTime(body.schedule_end)
    ) {
      errors.push(
        "A valid schedule_start and schedule_end are required (HH:MM)"
      );
    }
  }

  if (body.timezone !== undefined) {
    if (
      typeof body.timezone !== "string" ||
      body.timezone.length > 80
    ) {
      errors.push("Invalid timezone");
    }
  }

  return errors;
}

/*
  Replace all schedules of a zone.
*/
async function replaceSchedules(
  client,
  zoneId,
  schedules
) {
  await client.query(
    `DELETE FROM zone_schedules
     WHERE zone_id = $1`,
    [zoneId]
  );

  for (const schedule of schedules) {
    await client.query(
      `INSERT INTO zone_schedules
       (
         zone_id,
         name,
         start_time,
         end_time,
         days,
         is_active
       )
       VALUES
       (
         $1,
         $2,
         $3,
         $4,
         $5::smallint[],
         $6
       )`,
      [
        zoneId,
        schedule.name.trim(),
        schedule.start_time,
        schedule.end_time,
        normaliseDays(schedule.days) || ALL_DAYS,
        schedule.is_active ?? true
      ]
    );
  }
}

/*
  Get schedules of one zone.
*/
async function getZoneSchedules(
  client,
  zoneId
) {
  const result = await client.query(
    `SELECT
       id,
       zone_id,
       name,
       start_time,
       end_time,
       days,
       is_active,
       created_at,
       updated_at
     FROM zone_schedules
     WHERE zone_id = $1
     ORDER BY start_time ASC, name ASC`,
    [zoneId]
  );

  return result.rows;
}

/*
  Attach schedules to multiple zones.
*/
async function attachSchedules(
  rows,
  db = { query }
) {
  const zoneIds = rows.map(
    (zone) => zone.id
  );

  if (!zoneIds.length) {
    return rows.map((zone) => ({
      ...zone,
      schedules: []
    }));
  }

  const result = await db.query(
    `SELECT
       id,
       zone_id,
       name,
       start_time,
       end_time,
       days,
       is_active,
       created_at,
       updated_at
     FROM zone_schedules
     WHERE zone_id = ANY($1::uuid[])
     ORDER BY start_time ASC, name ASC`,
    [zoneIds]
  );

  const map = new Map();

  for (const schedule of result.rows) {
    if (!map.has(schedule.zone_id)) {
      map.set(schedule.zone_id, []);
    }

    map
      .get(schedule.zone_id)
      .push(schedule);
  }

  return rows.map((zone) => ({
    ...zone,
    schedules:
      map.get(zone.id) || []
  }));
}

/*
  PUBLIC MOBILE SYNC
  GET /api/zones
*/
router.get("/", async (_req, res) => {
  try {
    const result = await query(
      `SELECT
         z.id,
         z.area_id,
         a.name AS area_name,
         z.name,
         z.category,
         z.latitude,
         z.longitude,
         z.radius_meters AS radius_m,
         z.sound_action,
         z.schedule_enabled,
         z.schedule_start,
         z.schedule_end,
         z.timezone,
         z.version,
         z.updated_at
       FROM silent_zones z
       JOIN admin_areas a
         ON a.id = z.area_id
       WHERE z.is_active = TRUE
         AND a.is_active = TRUE
       ORDER BY z.updated_at DESC`
    );

    const zones =
      await attachSchedules(result.rows);

    return res.json({
      success: true,
      zones,
      server_time:
        new Date().toISOString()
    });
  } catch (error) {
    console.error(
      "Zone list failed:",
      error.message
    );

    return res.status(500).json({
      success: false,
      message: "Could not load zones"
    });
  }
});

/*
  AREAS THAT CURRENT ADMIN CAN MANAGE
  GET /api/zones/manageable-areas
*/
router.get(
  "/manageable-areas",
  authenticate,
  requireRoles(...ADMIN_ROLES),
  async (req, res) => {
    try {
      const result =
        req.user.role === "SUPER_ADMIN"
          ? await query(
              `SELECT
                 id,
                 name,
                 district,
                 state,
                 country,
                 is_active
               FROM admin_areas
               WHERE is_active = TRUE
               ORDER BY
                 state,
                 district,
                 name`
            )
          : await query(
              `SELECT
                 a.id,
                 a.name,
                 a.district,
                 a.state,
                 a.country,
                 a.is_active
               FROM admin_area_assignments aa
               JOIN admin_areas a
                 ON a.id = aa.area_id
               WHERE aa.user_id = $1
                 AND a.is_active = TRUE
               ORDER BY
                 a.state,
                 a.district,
                 a.name`,
              [req.user.id]
            );

      return res.json({
        success: true,
        areas: result.rows
      });
    } catch (error) {
      console.error(
        "Manageable area list failed:",
        error.message
      );

      return res.status(500).json({
        success: false,
        message:
          "Could not load assigned areas"
      });
    }
  }
);

/*
  ADMIN ZONE LIST
  GET /api/zones/admin
*/
router.get(
  "/admin",
  authenticate,
  requireRoles(...ADMIN_ROLES),
  async (req, res) => {
    try {
      let result;

      if (req.user.role === "SUPER_ADMIN") {
        result = await query(
          `SELECT
             z.id,
             z.area_id,
             a.name AS area_name,
             z.name,
             z.category,
             z.latitude,
             z.longitude,
             z.radius_meters AS radius_m,
             z.sound_action,
             z.schedule_enabled,
             z.schedule_start,
             z.schedule_end,
             z.timezone,
             z.version,
             z.updated_at
           FROM silent_zones z
           JOIN admin_areas a
             ON a.id = z.area_id
           WHERE z.is_active = TRUE
           ORDER BY z.updated_at DESC`
        );
      } else {
        result = await query(
          `SELECT
             z.id,
             z.area_id,
             a.name AS area_name,
             z.name,
             z.category,
             z.latitude,
             z.longitude,
             z.radius_meters AS radius_m,
             z.sound_action,
             z.schedule_enabled,
             z.schedule_start,
             z.schedule_end,
             z.timezone,
             z.version,
             z.updated_at
           FROM silent_zones z
           JOIN admin_areas a
             ON a.id = z.area_id
           JOIN admin_area_assignments aa
             ON aa.area_id = z.area_id
            AND aa.user_id = $1
           WHERE z.is_active = TRUE
             AND a.is_active = TRUE
           ORDER BY z.updated_at DESC`,
          [req.user.id]
        );
      }

      const zones =
        await attachSchedules(
          result.rows
        );

      return res.json({
        success: true,
        zones,
        server_time:
          new Date().toISOString()
      });
    } catch (error) {
      console.error(
        "Admin zone list failed:",
        error.message
      );

      return res.status(500).json({
        success: false,
        message:
          "Could not load zones"
      });
    }
  }
);

/*
  CREATE ZONE
  POST /api/zones
*/
router.post(
  "/",
  authenticate,
  requireRoles(...ADMIN_ROLES),
  async (req, res) => {
    const client = await pool.connect();

    try {
      const body = req.body || {};

      const errors =
        validateZone(body);

      if (
        !errors.length &&
        typeof body.area_id !== "string"
      ) {
        errors.push(
          "area_id is required"
        );
      }

      if (
        body.schedule_enabled === true &&
        Array.isArray(body.schedules) &&
        body.schedules.length === 0
      ) {
        errors.push(
          "At least one schedule is required when scheduling is enabled"
        );
      }

      if (errors.length) {
        return res.status(400).json({
          success: false,
          message:
            "Invalid zone data",
          errors
        });
      }

      if (
        !(await canManageArea(
          req.user,
          body.area_id
        ))
      ) {
        return res.status(403).json({
          success: false,
          message:
            "You cannot manage this area"
        });
      }

      await client.query("BEGIN");

      const usingMultipleSchedules =
        Array.isArray(
          body.schedules
        );

      const created =
        await client.query(
          `INSERT INTO silent_zones
           (
             area_id,
             name,
             category,
             latitude,
             longitude,
             radius_meters,
             sound_action,
             schedule_enabled,
             schedule_start,
             schedule_end,
             timezone,
             created_by,
             updated_by
           )
           VALUES
           (
             $1,
             $2,
             $3,
             $4,
             $5,
             $6,
             $7,
             $8,
             $9,
             $10,
             $11,
             $12,
             $12
           )
           RETURNING *`,
          [
            body.area_id,
            body.name.trim(),
            body.category,
            body.latitude,
            body.longitude,
            body.radius_m,
            body.sound_action,
            body.schedule_enabled ??
              false,

            usingMultipleSchedules
              ? null
              : body.schedule_start ??
                null,

            usingMultipleSchedules
              ? null
              : body.schedule_end ??
                null,

            body.timezone ??
              "Asia/Kolkata",

            req.user.id
          ]
        );

      const zone =
        created.rows[0];

      if (usingMultipleSchedules) {
        await replaceSchedules(
          client,
          zone.id,
          body.schedules
        );
      }

      const schedules =
        await getZoneSchedules(
          client,
          zone.id
        );

      const responseZone = {
        ...zone,
        radius_m:
          zone.radius_meters,
        schedules
      };

      await client.query(
        `INSERT INTO zone_audit_logs
         (
           zone_id,
           actor_user_id,
           action,
           details
         )
         VALUES
         (
           $1,
           $2,
           'CREATE',
           $3::jsonb
         )`,
        [
          zone.id,
          req.user.id,
          JSON.stringify(
            responseZone
          )
        ]
      );

      await client.query(
        "COMMIT"
      );

      return res.status(201).json({
        success: true,
        zone: responseZone
      });
    } catch (error) {
      try {
        await client.query(
          "ROLLBACK"
        );
      } catch {}

      console.error(
        "Zone creation failed:",
        error.message
      );

      return res.status(500).json({
        success: false,
        message:
          "Could not create zone",
        error: error.message
      });
    } finally {
      client.release();
    }
  }
);

/*
  UPDATE ZONE
  PATCH /api/zones/:id
*/
router.patch(
  "/:id",
  authenticate,
  requireRoles(...ADMIN_ROLES),
  async (req, res) => {
    const client =
      await pool.connect();

    try {
      const body =
        req.body || {};

      const allowedFields = [
        "area_id",
        "name",
        "category",
        "latitude",
        "longitude",
        "radius_m",
        "sound_action",
        "schedule_enabled",
        "schedule_start",
        "schedule_end",
        "timezone",
        "is_active",
        "schedules"
      ];

      const keys =
        Object.keys(body);

      if (
        !keys.length ||
        keys.some(
          (key) =>
            !allowedFields.includes(
              key
            )
        )
      ) {
        return res.status(400).json({
          success: false,
          message:
            "Provide only supported zone fields"
        });
      }

      const errors =
        validateZone(
          body,
          true
        );

      if (
        body.area_id !== undefined &&
        (
          typeof body.area_id !==
            "string" ||
          !body.area_id.trim()
        )
      ) {
        errors.push(
          "area_id must be a valid area ID"
        );
      }

      if (
        body.is_active !== undefined &&
        typeof body.is_active !==
          "boolean"
      ) {
        errors.push(
          "is_active must be a boolean"
        );
      }

      if (
        body.schedule_enabled ===
          true &&
        Array.isArray(
          body.schedules
        ) &&
        body.schedules.length === 0
      ) {
        errors.push(
          "At least one schedule is required when scheduling is enabled"
        );
      }

      if (errors.length) {
        return res.status(400).json({
          success: false,
          message:
            "Invalid zone data",
          errors
        });
      }

      await client.query(
        "BEGIN"
      );

      const found =
        await client.query(
          `SELECT *
           FROM silent_zones
           WHERE id = $1
           FOR UPDATE`,
          [req.params.id]
        );

      const oldZone =
        found.rows[0];

      if (!oldZone) {
        await client.query(
          "ROLLBACK"
        );

        return res.status(404).json({
          success: false,
          message:
            "Zone not found"
        });
      }

      if (
        !(await canManageArea(
          req.user,
          oldZone.area_id,
          client
        ))
      ) {
        await client.query(
          "ROLLBACK"
        );

        return res.status(403).json({
          success: false,
          message:
            "You cannot manage this zone"
        });
      }

      /*
        Only SUPER_ADMIN can move
        a zone to another area.
      */
      if (
        body.area_id !== undefined &&
        body.area_id !==
          oldZone.area_id
      ) {
        if (
          req.user.role !==
          "SUPER_ADMIN"
        ) {
          await client.query(
            "ROLLBACK"
          );

          return res.status(403).json({
            success: false,
            message:
              "Only SUPER_ADMIN can move a zone to another area"
          });
        }

        const targetArea =
          await client.query(
            `SELECT 1
             FROM admin_areas
             WHERE id = $1
               AND is_active = TRUE`,
            [body.area_id]
          );

        if (!targetArea.rowCount) {
          await client.query(
            "ROLLBACK"
          );

          return res.status(400).json({
            success: false,
            message:
              "Target area not found or inactive"
          });
        }
      }

      const zoneKeys =
        keys.filter(
          (key) =>
            key !== "schedules"
        );

      const fields = {
        area_id:
          "area_id",

        name:
          "name",

        category:
          "category",

        latitude:
          "latitude",

        longitude:
          "longitude",

        radius_m:
          "radius_meters",

        sound_action:
          "sound_action",

        schedule_enabled:
          "schedule_enabled",
        schedule_start:
          "schedule_start",

        schedule_end:
          "schedule_end",

        timezone:
          "timezone",

        is_active:
          "is_active"
      };

      const values = [];
      const sets = [];

      for (
        const key of zoneKeys
      ) {
        let value =
          body[key];
        if (
          key === "name"
        ) {
          value =
            value.trim();
        }

        if (
          key === "area_id"
        ) {
          value =
            value.trim();
        }

        values.push(value);

        sets.push(
          `${fields[key]} = $${values.length}`
        );
      }

      /*
        When schedules array is
        supplied, old single schedule
        fields are cleared.
      */
      if (
        Array.isArray(
          body.schedules
        )
      ) {
        values.push(null);

        sets.push(
          `schedule_start = $${values.length}`
        );

        values.push(null);

        sets.push(
          `schedule_end = $${values.length}`
        );
      }

      values.push(
        req.user.id
      );

      sets.push(
        `updated_by = $${values.length}`
      );
      sets.push(
        "updated_at = NOW()"
      );

      sets.push(
        "version = version + 1"
      );

      values.push(
        req.params.id
      );

      const updated =
        await client.query(
          `UPDATE silent_zones
           SET ${sets.join(", ")}
           WHERE id = $${values.length}
           RETURNING *`,
          values
        );

      if (
        Array.isArray(
          body.schedules
        )
      ) {
        await replaceSchedules(
          client,
          req.params.id,
          body.schedules
        );
      }

      const schedules =
        await getZoneSchedules(
          client,
          req.params.id
        );

      const responseZone = {
        ...updated.rows[0],
        radius_m:
          updated.rows[0]
            .radius_meters,
        schedules
      };

      await client.query(
        `INSERT INTO zone_audit_logs
        (
        zone_id,
           actor_user_id,
           action,
           details
         )
         VALUES
         (
           $1,
           $2,
           'UPDATE',
           $3::jsonb
         )`,
        [
          oldZone.id,
          req.user.id,
          JSON.stringify({
            old_data:
              oldZone,
            new_data:
              responseZone
          })
        ]
      );

      await client.query(
        "COMMIT"
      );
      return res.json({
        success: true,
        zone: responseZone,
        message:
          "Zone updated. Connected devices will sync on their next sync."
      });
    } catch (error) {
      try {
        await client.query(
          "ROLLBACK"
        );
      } catch {}

      console.error(
        "Zone update failed:",
        error.message
      );

      return res.status(500).json({
        success: false,
        message:
          "Could not update zone"
      });
    } finally {
      client.release();
    }
  }
);
export default router;
