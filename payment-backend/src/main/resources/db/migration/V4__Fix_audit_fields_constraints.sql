-- Remove NOT NULL constraint from created_at and allow it to be populated by JPA auditing
-- or set default values

-- Option 1: Remove NOT NULL constraint
-- ALTER TABLE users ALTER COLUMN created_at DROP NOT NULL;

-- Option 2: Set default values for audit fields (better approach)
ALTER TABLE users ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

-- For existing rows with null values, update them
UPDATE users SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
UPDATE users SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;