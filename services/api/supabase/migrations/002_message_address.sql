-- Apply after 001_vehicle_family.sql, before deploying this API version.
-- Additive; does not recreate tables or overwrite existing rows.
ALTER TABLE chamamothanmal.cm_messages ADD COLUMN IF NOT EXISTS address VARCHAR(500);
