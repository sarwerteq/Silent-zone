
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER', 'AREA_ADMIN', 'DISTRICT_ADMIN', 'SUPER_ADMIN')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS admin_areas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    area_type VARCHAR(30) NOT NULL DEFAULT 'AREA',
    parent_area_id UUID REFERENCES admin_areas(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS admin_area_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    area_id UUID NOT NULL REFERENCES admin_areas(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, area_id)
);

CREATE TABLE IF NOT EXISTS silent_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id UUID NOT NULL REFERENCES admin_areas(id),
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    radius_meters INTEGER NOT NULL DEFAULT 100
        CHECK (radius_meters BETWEEN 25 AND 5000),
    sound_action VARCHAR(20) NOT NULL DEFAULT 'VIBRATE'
        CHECK (sound_action IN ('SILENT', 'VIBRATE', 'DND')),
    schedule_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    schedule_start TIME,
    schedule_end TIME,
    timezone VARCHAR(100) NOT NULL DEFAULT 'Asia/Kolkata',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    created_by UUID REFERENCES app_users(id),
    updated_by UUID REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (
        schedule_enabled = FALSE
        OR (schedule_start IS NOT NULL AND schedule_end IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    device_token TEXT NOT NULL UNIQUE,
    platform VARCHAR(20) NOT NULL DEFAULT 'ANDROID',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS zone_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id UUID REFERENCES silent_zones(id) ON DELETE SET NULL,
    actor_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_silent_zones_area
    ON silent_zones(area_id);

CREATE INDEX IF NOT EXISTS idx_silent_zones_active
    ON silent_zones(is_active);

CREATE INDEX IF NOT EXISTS idx_area_assignments_user
    ON admin_area_assignments(user_id);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user
    ON device_tokens(user_id);

CREATE INDEX IF NOT EXISTS idx_zone_audit_created
    ON zone_audit_logs(created_at DESC);
