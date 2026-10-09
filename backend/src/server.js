
import "dotenv/config";
import express from "express";
import helmet from "helmet";
import cors from "cors";
import bcrypt from "bcryptjs";
import rateLimit from "express-rate-limit";
import { query } from "./db.js";
import authRouter from "./auth.js";
import zonesRouter from "./zones.js";
import adminRouter from "./admin.js";

const app = express();

if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 32) {
  console.error("Set JWT_SECRET to a random secret of at least 32 characters.");
  process.exit(1);
}

app.disable("x-powered-by");
app.use(helmet());
app.use(express.json({ limit: "100kb" }));

const allowedOrigins = (process.env.ALLOWED_ORIGINS || "")
  .split(",")
  .map((origin) => origin.trim())
  .filter(Boolean);

app.use(cors({
  origin(origin, callback) {
    if (!origin) return callback(null, true);
    if (allowedOrigins.includes(origin)) return callback(null, true);
    return callback(new Error("Origin not allowed"), false);
  }
}));

const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 20,
  standardHeaders: "draft-7",
  legacyHeaders: false,
  message: {
    success: false,
    message: "Too many authentication attempts. Try again later."
  }
});

app.get("/", (_req, res) => {
  res.json({ name: "Silent Zone API", status: "running" });
});

app.get("/api/health", async (_req, res) => {
  try {
    await query("SELECT 1");
    res.json({
      success: true,
      service: "silent-zone-api",
      database: "connected",
      timestamp: new Date().toISOString()
    });
  } catch {
    res.status(503).json({
      success: false,
      service: "silent-zone-api",
      database: "unavailable"
    });
  }
});

app.use("/api/auth", authLimiter, authRouter);
app.use("/api/zones", zonesRouter);
app.use("/api/admin", adminRouter);

app.use((_req, res) => {
  res.status(404).json({
    success: false,
    message: "Endpoint not found"
  });
});

app.use((err, _req, res, _next) => {
  if (err instanceof SyntaxError && "body" in err) {
    return res.status(400).json({
      success: false,
      message: "Invalid JSON request body"
    });
  }

  if (err.message === "Origin not allowed") {
    return res.status(403).json({
      success: false,
      message: "Request origin is not allowed"
    });
  }

  console.error("API error:", err.message);
  return res.status(500).json({
    success: false,
    message: "Internal server error"
  });
});

async function bootstrapSuperAdmin() {
  const email = process.env.BOOTSTRAP_ADMIN_EMAIL?.trim().toLowerCase();
  const password = process.env.BOOTSTRAP_ADMIN_PASSWORD;
  const displayName = process.env.BOOTSTRAP_ADMIN_NAME || "Super Admin";

  if (!email || !password) {
    console.warn("Bootstrap admin not configured; no admin was created.");
    return;
  }

  if (password.length < 16 || password.length > 72) {
    throw new Error("Bootstrap admin password must be 16–72 characters.");
  }

  const passwordHash = await bcrypt.hash(password, 12);

  await query(
    `INSERT INTO app_users (email, password_hash, display_name, role)
     VALUES ($1, $2, $3, 'SUPER_ADMIN')
     ON CONFLICT (email) DO NOTHING`,
    [email, passwordHash, displayName]
  );

  console.log("Bootstrap admin checked. Existing account passwords are not changed.");
}

const port = Number(process.env.PORT) || 3000;

app.listen(port, "0.0.0.0", () => {
  console.log(`Silent Zone API listening on port ${port}`);

  bootstrapSuperAdmin().catch((error) => {
    console.error("Super admin bootstrap failed:", error.message);
  });
});
