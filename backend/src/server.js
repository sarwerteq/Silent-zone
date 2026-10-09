
import "dotenv/config";
import express from "express";
import helmet from "helmet";
import cors from "cors";

const app = express();

app.disable("x-powered-by");
app.use(helmet());
app.use(express.json({ limit: "100kb" }));

const allowedOrigins = (process.env.ALLOWED_ORIGINS || "")
  .split(",")
  .map((origin) => origin.trim())
  .filter(Boolean);

app.use(
  cors({
    origin(origin, callback) {
      // Allow server-to-server requests and local tools without an Origin.
      if (!origin) return callback(null, true);

      if (allowedOrigins.length === 0) {
        return callback(
          new Error("CORS origin is not configured"),
          false
        );
      }

      if (allowedOrigins.includes(origin)) {
        return callback(null, true);
      }

      return callback(new Error("Origin not allowed"), false);
    }
  })
);

app.get("/", (_req, res) => {
  res.json({
    name: "Silent Zone API",
    status: "running",
    message: "Welcome to Silent Zone"
  });
});

app.get("/api/health", (_req, res) => {
  res.status(200).json({
    success: true,
    service: "silent-zone-api",
    status: "healthy",
    timestamp: new Date().toISOString()
  });
});

// Temporary endpoint until the database-backed zone API is added.
app.get("/api/zones", (_req, res) => {
  res.status(503).json({
    success: false,
    message: "Zones API is not configured yet."
  });
});

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

  if (err.message === "Origin not allowed" ||
      err.message === "CORS origin is not configured") {
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

const port = Number(process.env.PORT) || 3000;

app.listen(port, "0.0.0.0", () => {
  console.log(`Silent Zone API listening on port ${port}`);
});
