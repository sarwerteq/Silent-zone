
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER', 'AREA_ADMIN', 'DISTRICT_ADMIN', 'SUPER_ADMIN')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS admin_areas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    district TEXT NOT NULL,
    state TEXT NOT NULL,
    country TEXT NOT NULL DEFAULT 'India',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS admin_area_assignments (
    admin_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    area_id UUID NOT NULL REFERENCES admin_areas(id) ON DELETE CASCADE,
    assigned_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (admin_user_id, area_id)
);

CREATE TABLE IF NOT EXISTS silent_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id UUID NOT NULL REFERENCES admin_areas(id),
    name TEXT NOT NULL,
    category TEXT NOT NULL
        CHECK (category IN ('MOSQUE', 'SCHOOL', 'HOSPITAL', 'LIBRARY', 'OTHER')),
    latitude DOUBLE PRECISION NOT NULL
        CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL
        CHECK (longitude BETWEEN -180 AND 180),
    radius_m INTEGER NOT NULL
        CHECK (radius_m BETWEEN 50 AND 1000),
    sound_action TEXT NOT NULL DEFAULT 'SILENT'
        CHECK (sound_action IN ('SILENT', 'VIBRATE', 'DND')),
    schedule_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    schedule_start TIME,
    schedule_end TIME,
    timezone TEXT NOT NULL DEFAULT 'Asia/Kolkata',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    created_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (
      NOT schedule_enabled OR
      (schedule_start IS NOT NULL AND schedule_end IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_silent_zones_area
    ON silent_zones(area_id);

CREATE INDEX IF NOT EXISTS idx_silent_zones_active
    ON silent_zones(is_active);

CREATE TABLE IF NOT EXISTS device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    fcm_token TEXT NOT NULL UNIQUE,
    platform TEXT NOT NULL DEFAULT 'ANDROID'
        CHECK (platform IN ('ANDROID', 'WEB')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user
    ON device_tokens(user_id, is_active);

CREATE TABLE IF NOT EXISTS zone_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id UUID NOT NULL REFERENCES silent_zones(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    old_data JSONB,
    new_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_zone_audit_logs_zone
    ON zone_audit_logs(zone_id, created_at DESC);
