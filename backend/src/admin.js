
import express from "express";
import bcrypt from "bcryptjs";
import { query } from "./db.js";
import { authenticate, requireRoles } from "./auth.js";

const router = express.Router();

router.use(authenticate, requireRoles("SUPER_ADMIN"));

router.get("/areas", async (_req, res) => {
  try {
    const result = await query(
      `SELECT id, name, district, state, country, is_active, created_at
       FROM admin_areas ORDER BY state, district, name`
    );

    res.json({ success: true, areas: result.rows });
  } catch (error) {
    console.error("Area listing failed:", error.message);
    res.status(500).json({ success: false, message: "Could not load areas" });
  }
});

router.post("/areas", async (req, res) => {
  try {
    const { name, district, state, country = "India" } = req.body || {};

    if (
      typeof name !== "string" || !name.trim() || name.length > 120 ||
      typeof district !== "string" || !district.trim() || district.length > 120 ||
      typeof state !== "string" || !state.trim() || state.length > 120 ||
      typeof country !== "string" || !country.trim() || country.length > 120
    ) {
      return res.status(400).json({
        success: false,
        message: "Valid name, district, state and country are required"
      });
    }

    const result = await query(
      `INSERT INTO admin_areas (name, district, state, country)
       VALUES ($1, $2, $3, $4)
       RETURNING *`,
      [name.trim(), district.trim(), state.trim(), country.trim()]
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

router.post("/admins", async (req, res) => {
  try {
    const { email, password, display_name, role } = req.body || {};

    if (
      typeof email !== "string" ||
      typeof password !== "string" ||
      typeof display_name !== "string" ||
      !["AREA_ADMIN", "DISTRICT_ADMIN"].includes(role)
    ) {
      return res.status(400).json({
        success: false,
        message: "Email, password, display_name and an allowed admin role are required"
      });
    }

    const normalizedEmail = email.trim().toLowerCase();
    const name = display_name.trim();

    if (
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail) ||
      normalizedEmail.length > 254 ||
      password.length < 10 ||
      password.length > 72 ||
      !name ||
      name.length > 100
    ) {
      return res.status(400).json({
        success: false,
        message: "Invalid email, password or display name"
      });
    }

    const passwordHash = await bcrypt.hash(password, 12);

    const result = await query(
      `INSERT INTO app_users (email, password_hash, display_name, role)
       VALUES ($1, $2, $3, $4)
       RETURNING id, email, display_name, role, is_active`,
      [normalizedEmail, passwordHash, name, role]
    );

    return res.status(201).json({
      success: true,
      admin: result.rows[0],
      message: "Admin created. Assign an area before allowing zone management."
    });
  } catch (error) {
    if (error.code === "23505") {
      return res.status(409).json({
        success: false,
        message: "Email is already registered"
      });
    }

    console.error("Admin creation failed:", error.message);
    return res.status(500).json({
      success: false,
      message: "Could not create admin"
    });
  }
});

router.post("/assignments", async (req, res) => {
  try {
    const { admin_user_id, area_id } = req.body || {};

    if (
      typeof admin_user_id !== "string" ||
      typeof area_id !== "string"
    ) {
      return res.status(400).json({
        success: false,
        message: "admin_user_id and area_id are required"
      });
    }

    const result = await query(
      `INSERT INTO admin_area_assignments (admin_user_id, area_id, assigned_by)
       SELECT u.id, a.id, $3
       FROM app_users u
       JOIN admin_areas a ON a.id = $2
       WHERE u.id = $1
         AND u.role IN ('AREA_ADMIN', 'DISTRICT_ADMIN')
         AND u.is_active = TRUE
         AND a.is_active = TRUE
       ON CONFLICT (admin_user_id, area_id) DO NOTHING
       RETURNING admin_user_id, area_id, assigned_by, created_at`,
      [admin_user_id, area_id, req.user.id]
    );

    if (result.rowCount === 0) {
      return res.status(400).json({
        success: false,
        message: "Admin or active area not found, or assignment already exists"
      });
    }

    return res.status(201).json({
      success: true,
      assignment: result.rows[0]
    });
  } catch (error) {
    console.error("Assignment failed:", error.message);
    return res.status(500).json({
      success: false,
      message: "Could not assign area"
    });
  }
});

export default router;
