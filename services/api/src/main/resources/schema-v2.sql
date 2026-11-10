CREATE TABLE IF NOT EXISTS cm_users (
 id VARCHAR(64) PRIMARY KEY, state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
);
CREATE TABLE IF NOT EXISTS cm_families (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(120) NOT NULL, owner_id VARCHAR(64) NOT NULL REFERENCES cm_users(id), active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS cm_members (
 family_id VARCHAR(36) NOT NULL REFERENCES cm_families(id), user_id VARCHAR(64) NOT NULL REFERENCES cm_users(id),
 can_send BOOLEAN NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, PRIMARY KEY(family_id,user_id)
);
CREATE TABLE IF NOT EXISTS cm_vehicles (
 id VARCHAR(36) PRIMARY KEY, family_id VARCHAR(36) NOT NULL REFERENCES cm_families(id), name VARCHAR(120) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, revision BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS cm_codes (
 code_hash VARCHAR(64) PRIMARY KEY, family_id VARCHAR(36) NOT NULL REFERENCES cm_families(id),
 vehicle_id VARCHAR(36) REFERENCES cm_vehicles(id), kind VARCHAR(16) NOT NULL, expires_at BIGINT NOT NULL,
 consumed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS cm_devices (
 id VARCHAR(36) PRIMARY KEY, vehicle_id VARCHAR(36) NOT NULL REFERENCES cm_vehicles(id),
 user_id VARCHAR(64) NOT NULL REFERENCES cm_users(id), token_hash VARCHAR(64) NOT NULL UNIQUE, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS cm_messages (
 id VARCHAR(36) PRIMARY KEY, sender_id VARCHAR(64) NOT NULL REFERENCES cm_users(id),
 vehicle_id VARCHAR(36) NOT NULL REFERENCES cm_vehicles(id), request_id VARCHAR(36) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 place_name VARCHAR(120) NOT NULL, latitude DOUBLE PRECISION NOT NULL, longitude DOUBLE PRECISION NOT NULL,
 radius_meters INTEGER NOT NULL, audio_name VARCHAR(64) NOT NULL, audio_bytes BIGINT NOT NULL, audio_seconds DOUBLE PRECISION NOT NULL,
 created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, completed_at BIGINT, deleted_at BIGINT,
 audio_removed BOOLEAN NOT NULL DEFAULT FALSE, UNIQUE(sender_id,request_id)
);
CREATE INDEX IF NOT EXISTS cm_messages_vehicle_idx ON cm_messages(vehicle_id,expires_at);
CREATE TABLE IF NOT EXISTS cm_sessions (
 id VARCHAR(36) PRIMARY KEY, vehicle_id VARCHAR(36) NOT NULL REFERENCES cm_vehicles(id),
 device_id VARCHAR(36) NOT NULL REFERENCES cm_devices(id), driver_id VARCHAR(64) NOT NULL REFERENCES cm_users(id),
 destination_latitude DOUBLE PRECISION NOT NULL, destination_longitude DOUBLE PRECISION NOT NULL,
 source VARCHAR(16) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, end_reason VARCHAR(32),
 created_at BIGINT NOT NULL, ended_at BIGINT, request_id VARCHAR(36) NOT NULL, request_hash VARCHAR(64) NOT NULL, UNIQUE(vehicle_id,request_id)
);
CREATE TABLE IF NOT EXISTS cm_deliveries (
 session_id VARCHAR(36) NOT NULL REFERENCES cm_sessions(id), message_id VARCHAR(36) NOT NULL REFERENCES cm_messages(id),
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING', version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(session_id,message_id)
);
CREATE TABLE IF NOT EXISTS cm_events (
 session_id VARCHAR(36) NOT NULL, message_id VARCHAR(36) NOT NULL, event_id VARCHAR(36) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL, status VARCHAR(16) NOT NULL, version BIGINT NOT NULL, source VARCHAR(16) NOT NULL, reason VARCHAR(32),
 recorded_at BIGINT NOT NULL, PRIMARY KEY(session_id,message_id,event_id),
 FOREIGN KEY(session_id,message_id) REFERENCES cm_deliveries(session_id,message_id)
);
CREATE TABLE IF NOT EXISTS cm_replies (
 id VARCHAR(36) PRIMARY KEY, session_id VARCHAR(36) NOT NULL, message_id VARCHAR(36) NOT NULL,
 author_id VARCHAR(64) NOT NULL REFERENCES cm_users(id), kind VARCHAR(32) NOT NULL, created_at BIGINT NOT NULL,
 UNIQUE(session_id,message_id), FOREIGN KEY(session_id,message_id) REFERENCES cm_deliveries(session_id,message_id)
);

CREATE TABLE IF NOT EXISTS cm_auth_deletions (
 user_id VARCHAR(64) PRIMARY KEY REFERENCES cm_users(id), created_at BIGINT NOT NULL, completed BOOLEAN NOT NULL DEFAULT FALSE
);
