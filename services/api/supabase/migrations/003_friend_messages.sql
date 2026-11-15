BEGIN;
SET LOCAL search_path TO chamamothanmal;
CREATE TABLE IF NOT EXISTS cm_friends (
 owner_id VARCHAR(64) NOT NULL REFERENCES cm_users(id),
 friend_id VARCHAR(64) NOT NULL REFERENCES cm_users(id),
 PRIMARY KEY(owner_id, friend_id), CHECK(owner_id <> friend_id)
);
CREATE TABLE IF NOT EXISTS cm_friend_messages (
 id VARCHAR(36) PRIMARY KEY,
 sender_id VARCHAR(64) NOT NULL REFERENCES cm_users(id),
 recipient_id VARCHAR(64) NOT NULL REFERENCES cm_users(id),
 request_id VARCHAR(36) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 place_name VARCHAR(120) NOT NULL, address VARCHAR(500),
 latitude DOUBLE PRECISION NOT NULL, longitude DOUBLE PRECISION NOT NULL,
 listening_mode VARCHAR(16) NOT NULL CHECK(listening_mode IN ('TOGETHER','PRIVATE')),
 audio_name VARCHAR(64) NOT NULL, audio_seconds DOUBLE PRECISION NOT NULL,
 created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL,
 completed_at BIGINT, archive_until BIGINT, deleted_at BIGINT,
 audio_removed BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(sender_id,request_id)
);
CREATE INDEX IF NOT EXISTS cm_friend_inbox_idx ON cm_friend_messages(recipient_id,created_at);

ALTER TABLE cm_friends ENABLE ROW LEVEL SECURITY;
ALTER TABLE cm_friend_messages ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON cm_friends, cm_friend_messages FROM anon, authenticated;
COMMIT;
