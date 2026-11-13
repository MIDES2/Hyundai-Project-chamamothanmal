CREATE TABLE IF NOT EXISTS cm_ui_accounts (
 id VARCHAR(64) PRIMARY KEY REFERENCES cm_users(id) ON DELETE CASCADE,
 name VARCHAR(30) NOT NULL, handle VARCHAR(100) NOT NULL UNIQUE, phone VARCHAR(20) NOT NULL,
 account_role VARCHAR(16) NOT NULL CHECK(account_role IN ('driver','recorder')),
 password_salt VARCHAR(32) NOT NULL, password_hash VARCHAR(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS cm_ui_logins (
 login_key VARCHAR(100) PRIMARY KEY,
 account_id VARCHAR(64) NOT NULL REFERENCES cm_ui_accounts(id) ON DELETE CASCADE
);
ALTER TABLE cm_friend_messages ADD COLUMN IF NOT EXISTS scheduled_date DATE;
ALTER TABLE cm_friend_messages ADD COLUMN IF NOT EXISTS available_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE cm_friend_messages ADD COLUMN IF NOT EXISTS reaction VARCHAR(16);
