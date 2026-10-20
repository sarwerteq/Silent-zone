import express from "express";
import bcrypt from "bcryptjs";
import { query } from "./db.js";
import { authenticate, requireRoles } from "./auth.js";

const router = express.Router();

router.use(authenticate, requireRoles("SUPER_ADMIN"));

/*
 * =========================================================
 * AREAS
 * =========================================================
 */

/*
 * GET ALL AREAS
 *
 * Active + inactive dono return honge.
 */
router.get("/areas", async (_req, res) => {
  try {
    const result = await query(
      `SELECT
         id,
         name,
         district,
         state,
         country,
         is_active,
         created_at
       FROM admin_areas
       ORDER BY
         is_active DESC,
         state,
         district,
         name`
    );

    return res.json({
      success: true,
      areas: result.rows
    });
  } catch (error) {
    console.error("Area listing failed:", error.message);

    return res.status(500).json({
      success: false,
      message: "Could not load areas"
    });
  }
});


/*
 * CREATE AREA
 */
router.post("/areas", async (req, res) => {
  try {
    const {
      name,
      district,
      state,
      country = "India"
    } = req.body || {};

    if (
      typeof name !== "string" ||
      !name.trim() ||
      name.length > 120 ||

      typeof district !== "string" ||
      !district.trim() ||
      district.length > 120 ||

      typeof state !== "string" ||
      !state.trim() ||
      state.length > 120 ||

      typeof country !== "string" ||
      !country.trim() ||
      country.length > 120
    ) {
      return res.status(400).json({
        success: false,
        message:
          "Valid name, district, state and country are required"
      });
    }

    const result = await query(
      `INSERT INTO admin_areas (
         name,
         district,
         state,
         country
       )
       VALUES ($1, $2, $3, $4)
       RETURNING *`,
      [
        name.trim(),
        district.trim(),
        state.trim(),
        country.trim()
      ]
    );

    return res.status(201).json({
      success: true,
      area: result.rows[0]
    });
  } catch (error) {
    console.error("Area creation failed:", error.message);

    return res.status(500).json({
      success: false,
      message: "Could not create area"
    });
  }
});


/*
 * ACTIVATE / DEACTIVATE AREA
 *
 * Body:
 *
 * {
 *   "is_active": false
 * }
 *
 * OR
 *
 * {
 *   "is_active": true
 * }
 */
router.patch("/areas/:id", async (req, res) => {
  try {
    const areaId = req.params.id;
    const { is_active } = req.body || {};

    if (typeof is_active !== "boolean") {
      return res.status(400).json({
        success: false,
        message: "is_active must be true or false"
      });
    }

    const result = await query(
      `UPDATE admin_areas
       SET is_active = $2
       WHERE id = $1
       RETURNING
         id,
         name,
         district,
         state,
         country,
         is_active,
         created_at`,
      [
        areaId,
        is_active
      ]
    );

    if (result.rowCount === 0) {
      return res.status(404).json({
        success: false,
        message: "Area not found"
      });
    }

    return res.json({
      success: true,
      area: result.rows[0],
      message: is_active
        ? "Area activated successfully"
        : "Area deactivated successfully"
    });
  } catch (error) {
    console.error(
      "Area status update failed:",
      error.message
    );

    return res.status(500).json({
      success: false,
      message: "Could not update area status"
    });
  }
});


/*
 * DELETE AREA
 *
 * IMPORTANT:
 *
 * Area tabhi permanently delete hoga jab:
 *
 * 1. Us area mein koi Silent Zone nahi ho.
 * 2. Kisi admin ko wo area assigned nahi ho.
 *
 * Existing data automatically delete nahi karenge.
 */
router.delete("/areas/:id", async (req, res) => {
  try {
    const areaId = req.params.id;

    /*
     * First make sure area exists.
     */
    const areaResult = await query(
      `SELECT
         id,
         name,
         district,
         state,
         country,
         is_active
       FROM admin_areas
       WHERE id = $1`,
      [areaId]
    );

    if (areaResult.rowCount === 0) {
      return res.status(404).json({
        success: false,
        message: "Area not found"
      });
    }

    /*
     * Check Silent Zones.
     */
    const zoneResult = await query(
      `SELECT COUNT(*)::int AS count
       FROM silent_zones
       WHERE area_id = $1`,
      [areaId]
    );

    const zoneCount =
      Number(zoneResult.rows[0]?.count || 0);

    /*
     * Check admin assignments.
     */
    const assignmentResult = await query(
      `SELECT COUNT(*)::int AS count
       FROM admin_area_assignments
       WHERE area_id = $1`,
      [areaId]
    );

    const assignmentCount =
      Number(
        assignmentResult.rows[0]?.count || 0
      );

    /*
     * Do NOT automatically delete dependencies.
     */
    if (
      zoneCount > 0 ||
      assignmentCount > 0
    ) {
      let reason = "";

      if (
        zoneCount > 0 &&
        assignmentCount > 0
      ) {
        reason =
          `${zoneCount} zone(s) and ` +
          `${assignmentCount} admin assignment(s)`;
      } else if (zoneCount > 0) {
        reason =
          `${zoneCount} zone(s)`;
      } else {
        reason =
          `${assignmentCount} admin assignment(s)`;
      }

      return res.status(409).json({
        success: false,
        message:
          `This area cannot be deleted because it has ${reason}. ` +
          `Deactivate the area instead.`
      });
    }

    /*
     * Safe permanent delete.
     */
    const deleteResult = await query(
      `DELETE FROM admin_areas
       WHERE id = $1
       RETURNING id, name`,
      [areaId]
    );

    if (deleteResult.rowCount === 0) {
      return res.status(404).json({
        success: false,
        message: "Area not found"
      });
    }

    return res.json({
      success: true,
      deleted_area: deleteResult.rows[0],
      message: "Area deleted permanently"
    });
  } catch (error) {
    /*
     * PostgreSQL foreign key violation.
     *
     * Extra safety in case some other table
     * also references admin_areas.
     */
    if (error.code === "23503") {
      return res.status(409).json({
        success: false,
        message:
          "This area is still being used by other records. " +
          "Deactivate it instead."
      });
    }

    console.error(
      "Area deletion failed:",
      error.message
    );

    return res.status(500).json({
      success: false,
      message: "Could not delete area"
    });
  }
});


/*
 * =========================================================
 * ADMIN ACCOUNTS
 * =========================================================
 */

router.post("/admins", async (req, res) => {
  try {
    const {
      email,
      password,
      display_name,
      role
    } = req.body || {};

    if (
      typeof email !== "string" ||
      typeof password !== "string" ||
      typeof display_name !== "string" ||
      ![
        "AREA_ADMIN",
        "DISTRICT_ADMIN"
      ].includes(role)
    ) {
      return res.status(400).json({
        success: false,
        message:
          "Email, password, display_name and an allowed admin role are required"
      });
    }

    const normalizedEmail =
      email.trim().toLowerCase();

    const name =
      display_name.trim();

    if (
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(
        normalizedEmail
      ) ||
      normalizedEmail.length > 254 ||
      password.length < 10 ||
      password.length > 72 ||
      !name ||
      name.length > 100
    ) {
      return res.status(400).json({
        success: false,
        message:
          "Invalid email, password or display name"
      });
    }

    const passwordHash =
      await bcrypt.hash(
        password,
        12
      );

    const result = await query(
      `INSERT INTO app_users (
         email,
         password_hash,
         display_name,
         role
       )
       VALUES ($1, $2, $3, $4)
       RETURNING
         id,
         email,
         display_name,
         role,
         is_active`,
      [
        normalizedEmail,
        passwordHash,
        name,
        role
      ]
    );

    return res.status(201).json({
      success: true,
      admin: result.rows[0],
      message:
        "Admin created. Assign an area before allowing zone management."
    });
  } catch (error) {
    if (error.code === "23505") {
      return res.status(409).json({
        success: false,
        message:
          "Email is already registered"
      });
    }

    console.error(
      "Admin creation failed:",
      error.message
    );

    return res.status(500).json({
      success: false,
      message:
        "Could not create admin"
    });
  }
});


/*
 * =========================================================
 * AREA ASSIGNMENTS
 * =========================================================
 */

router.post("/assignments", async (req, res) => {
  try {
    const {
      admin_user_id,
      area_id
    } = req.body || {};

    if (
      typeof admin_user_id !== "string" ||
      typeof area_id !== "string"
    ) {
      return res.status(400).json({
        success: false,
        message:
          "admin_user_id and area_id are required"
      });
    }

    const result = await query(
      `INSERT INTO admin_area_assignments (
         user_id,
         area_id
       )
       SELECT
         u.id,
         a.id
       FROM app_users u
       JOIN admin_areas a
         ON a.id = $2
       WHERE u.id = $1
         AND u.role IN (
           'AREA_ADMIN',
           'DISTRICT_ADMIN'
         )
         AND u.is_active = TRUE
         AND a.is_active = TRUE
       ON CONFLICT (
         user_id,
         area_id
       )
       DO NOTHING
       RETURNING
         user_id,
         area_id,
         created_at`,
      [
        admin_user_id,
        area_id
      ]
    );

    if (result.rowCount === 0) {
      return res.status(400).json({
        success: false,
        message:
          "Admin or active area not found, or assignment already exists"
      });
    }

    return res.status(201).json({
      success: true,
      assignment: result.rows[0]
    });
  } catch (error) {
    console.error(
      "Assignment failed:",
      error.message
    );

    return res.status(500).json({
      success: false,
      message:
        "Could not assign area"
    });
  }
});

export default router;
