CREATE TABLE IF NOT EXISTS app_users (
  id VARCHAR(64) PRIMARY KEY,
  user_role VARCHAR(16) NOT NULL
);
CREATE TABLE IF NOT EXISTS send_permissions (
  sender_id VARCHAR(64) NOT NULL REFERENCES app_users(id),
  recipient_id VARCHAR(64) NOT NULL REFERENCES app_users(id),
  PRIMARY KEY (sender_id, recipient_id)
);
CREATE TABLE IF NOT EXISTS messages (
  id VARCHAR(36) PRIMARY KEY,
  sender_id VARCHAR(64) NOT NULL REFERENCES app_users(id),
  recipient_id VARCHAR(64) NOT NULL REFERENCES app_users(id),
  request_id VARCHAR(36) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  place_name VARCHAR(120) NOT NULL,
  latitude DOUBLE PRECISION NOT NULL,
  longitude DOUBLE PRECISION NOT NULL,
  scheduled_date DATE NOT NULL,
  radius_meters INTEGER NOT NULL,
  require_parked BOOLEAN NOT NULL,
  audio_name VARCHAR(64) NOT NULL,
  audio_type VARCHAR(40) NOT NULL,
  audio_bytes BIGINT NOT NULL,
  audio_seconds DOUBLE PRECISION NOT NULL,
  playback_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  playback_version BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  UNIQUE (sender_id, request_id)
);
CREATE INDEX IF NOT EXISTS idx_messages_recipient_date ON messages(recipient_id, scheduled_date, created_at);
CREATE TABLE IF NOT EXISTS playback_events (
  message_id VARCHAR(36) NOT NULL REFERENCES messages(id),
  event_id VARCHAR(36) NOT NULL,
  playback_status VARCHAR(16) NOT NULL,
  expected_version BIGINT NOT NULL,
  result_version BIGINT NOT NULL,
  event_source VARCHAR(16) NOT NULL,
  reason VARCHAR(40),
  recorded_at BIGINT NOT NULL,
  PRIMARY KEY (message_id, event_id)
);
-- Fictional prototype identities only. This is not a user registration system.
MERGE INTO app_users (id, user_role) KEY(id) VALUES ('demo-sender', 'SENDER');
MERGE INTO app_users (id, user_role) KEY(id) VALUES ('demo-recipient', 'RECIPIENT');
MERGE INTO app_users (id, user_role) KEY(id) VALUES ('demo-other', 'RECIPIENT');
MERGE INTO send_permissions (sender_id, recipient_id) KEY(sender_id, recipient_id) VALUES ('demo-sender', 'demo-recipient');
MERGE INTO send_permissions (sender_id, recipient_id) KEY(sender_id, recipient_id) VALUES ('demo-sender', 'demo-other');
