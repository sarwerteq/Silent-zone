
import express from "express";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { query } from "./db.js";

const router = express.Router();

function createToken(user) {
  return jwt.sign(
    {
      sub: user.id,
      role: user.role
    },
    process.env.JWT_SECRET,
    { expiresIn: "12h", issuer: "silent-zone-api" }
  );
}

export function authenticate(req, res, next) {
  const header = req.headers.authorization || "";
  const [scheme, token] = header.split(" ");

  if (scheme !== "Bearer" || !token) {
    return res.status(401).json({
      success: false,
      message: "Login required"
    });
  }

  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET, {
      issuer: "silent-zone-api"
    });

    req.auth = {
      userId: payload.sub,
      tokenRole: payload.role
    };

    return next();
  } catch {
    return res.status(401).json({
      success: false,
      message: "Invalid or expired token"
    });
  }
}

export function requireRoles(...roles) {
  return async (req, res, next) => {
    try {
      const result = await query(
        `SELECT id, email, display_name, role, is_active
         FROM app_users WHERE id = $1`,
        [req.auth.userId]
      );

      const user = result.rows[0];

      if (!user || !user.is_active) {
        return res.status(401).json({
          success: false,
          message: "Account is unavailable"
        });
      }

      // Always trust the current database role, not the token role.
      req.user = user;

      if (!roles.includes(user.role)) {
        return res.status(403).json({
          success: false,
          message: "Insufficient permissions"
        });
      }

      return next();
    } catch (error) {
      console.error("Role check failed:", error.message);
      return res.status(500).json({
        success: false,
        message: "Could not verify permissions"
      });
    }
  };
}

router.post("/register", async (req, res) => {
  try {
    const { email, password, display_name } = req.body || {};

    if (
      typeof email !== "string" ||
      typeof password !== "string" ||
      typeof display_name !== "string"
    ) {
      return res.status(400).json({
        success: false,
        message: "Email, password and display name are required"
      });
    }

    const normalizedEmail = email.trim().toLowerCase();
    const name = display_name.trim();

    if (
      normalizedEmail.length > 254 ||
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail) ||
      password.length < 10 ||
      password.length > 72 ||
      name.length < 1 ||
      name.length > 100
    ) {
      return res.status(400).json({
        success: false,
        message: "Check email, name and password requirements"
      });
    }

    const passwordHash = await bcrypt.hash(password, 12);

    const result = await query(
      `INSERT INTO app_users (email, password_hash, display_name, role)
       VALUES ($1, $2, $3, 'USER')
       RETURNING id, email, display_name, role`,
      [normalizedEmail, passwordHash, name]
    );

    const user = result.rows[0];

    return res.status(201).json({
      success: true,
      user,
      token: createToken(user)
    });
  } catch (error) {
    if (error.code === "23505") {
      return res.status(409).json({
        success: false,
        message: "Email is already registered"
      });
    }

    console.error("Registration failed:", error.message);
    return res.status(500).json({
      success: false,
      message: "Registration failed"
    });
  }
});

router.post("/login", async (req, res) => {
  try {
    const { email, password } = req.body || {};

    if (
      typeof email !== "string" ||
      typeof password !== "string" ||
      email.length > 254 ||
      password.length > 72
    ) {
      return res.status(400).json({
        success: false,
        message: "Email and password are required"
      });
    }

    const result = await query(
      `SELECT id, email, display_name, role, password_hash, is_active
       FROM app_users WHERE email = $1`,
      [email.trim().toLowerCase()]
    );

    const user = result.rows[0];

    if (
      !user ||
      !user.is_active ||
      !(await bcrypt.compare(password, user.password_hash))
    ) {
      return res.status(401).json({
        success: false,
        message: "Invalid email or password"
      });
    }

    return res.json({
      success: true,
      user: {
        id: user.id,
        email: user.email,
        display_name: user.display_name,
        role: user.role
      },
      token: createToken(user)
    });
  } catch (error) {
    console.error("Login failed:", error.message);
    return res.status(500).json({
      success: false,
      message: "Login failed"
    });
  }
});

router.get("/me", authenticate, requireRoles(
  "USER", "AREA_ADMIN", "DISTRICT_ADMIN", "SUPER_ADMIN"
), (req, res) => {
  res.json({
    success: true,
    user: {
      id: req.user.id,
      email: req.user.email,
      display_name: req.user.display_name,
      role: req.user.role
    }
  });
});

export default router;
