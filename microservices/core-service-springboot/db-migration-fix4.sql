-- ============================================================
-- Ticklora Security & DB Hardening Migration
-- FIX 4: Database Design Improvements
-- Run this against your existing connectit_db database.
-- Safe to run multiple times — uses IF NOT EXISTS / IF EXISTS guards.
-- ============================================================

USE connectit_db;

-- ============================================================
-- FIX 4A: Add FK constraint columns to tickets table
-- Adds sla_policy_id and assignment_group_id FK columns
-- (Does NOT remove or rename any existing column)
-- ============================================================

-- Add sla_policy_id FK column if it doesn't exist
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'connectit_db'
      AND TABLE_NAME   = 'tickets'
      AND COLUMN_NAME  = 'sla_policy_id'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE tickets ADD COLUMN sla_policy_id BIGINT NULL',
    'SELECT ''sla_policy_id already exists, skipping'' AS info'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Add assignment_group_id FK column if it doesn't exist
SET @col2_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'connectit_db'
      AND TABLE_NAME   = 'tickets'
      AND COLUMN_NAME  = 'assignment_group_id'
);

SET @sql2 = IF(@col2_exists = 0,
    'ALTER TABLE tickets ADD COLUMN assignment_group_id VARCHAR(128) NULL',
    'SELECT ''assignment_group_id already exists, skipping'' AS info'
);
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- Add FK constraint: tickets.sla_policy_id -> sla_policies.id
SET @fk1_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA     = 'connectit_db'
      AND TABLE_NAME       = 'tickets'
      AND CONSTRAINT_NAME  = 'fk_ticket_sla_policy'
      AND CONSTRAINT_TYPE  = 'FOREIGN KEY'
);

SET @fk1_sql = IF(@fk1_exists = 0,
    'ALTER TABLE tickets ADD CONSTRAINT fk_ticket_sla_policy FOREIGN KEY (sla_policy_id) REFERENCES sla_policies(id) ON DELETE SET NULL',
    'SELECT ''fk_ticket_sla_policy already exists, skipping'' AS info'
);
PREPARE fk1_stmt FROM @fk1_sql; EXECUTE fk1_stmt; DEALLOCATE PREPARE fk1_stmt;

-- Add FK constraint: tickets.assignment_group_id -> settings_groups.id
SET @fk2_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA     = 'connectit_db'
      AND TABLE_NAME       = 'tickets'
      AND CONSTRAINT_NAME  = 'fk_ticket_assignment_group'
      AND CONSTRAINT_TYPE  = 'FOREIGN KEY'
);

SET @fk2_sql = IF(@fk2_exists = 0,
    'ALTER TABLE tickets ADD CONSTRAINT fk_ticket_assignment_group FOREIGN KEY (assignment_group_id) REFERENCES settings_groups(id) ON DELETE SET NULL',
    'SELECT ''fk_ticket_assignment_group already exists, skipping'' AS info'
);
PREPARE fk2_stmt FROM @fk2_sql; EXECUTE fk2_stmt; DEALLOCATE PREPARE fk2_stmt;

-- ============================================================
-- FIX 4A: Indexes on new FK columns
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_sla_policy_id ON tickets(sla_policy_id);
CREATE INDEX IF NOT EXISTS idx_assignment_group_id ON tickets(assignment_group_id);

-- ============================================================
-- FIX 4B: Missing indexes on frequently queried FK columns
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_tickets_caller_user_id ON tickets(caller_user_id);
CREATE INDEX IF NOT EXISTS idx_tickets_created_by ON tickets(created_by);
CREATE INDEX IF NOT EXISTS idx_ticket_activities_created_by ON ticket_activities(created_by);
CREATE INDEX IF NOT EXISTS idx_approvals_approved_by ON approvals(approved_by);
CREATE INDEX IF NOT EXISTS idx_timesheets_user_id ON timesheets(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);

-- call_logs.ticket_id index (only if table exists)
SET @tbl_call_logs = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'connectit_db' AND TABLE_NAME = 'call_logs'
);
SET @idx_call_sql = IF(@tbl_call_logs > 0,
    'CREATE INDEX IF NOT EXISTS idx_call_logs_ticket_id ON call_logs(ticket_id)',
    'SELECT ''call_logs table not found, skipping'' AS info'
);
PREPARE idx_call_stmt FROM @idx_call_sql; EXECUTE idx_call_stmt; DEALLOCATE PREPARE idx_call_stmt;

-- audit_log.entity_id index (only if table exists)
SET @tbl_audit = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'connectit_db' AND TABLE_NAME = 'audit_log'
);
SET @idx_audit_sql = IF(@tbl_audit > 0,
    'CREATE INDEX IF NOT EXISTS idx_audit_log_entity_id ON audit_log(entity_id)',
    'SELECT ''audit_log table not found, skipping'' AS info'
);
PREPARE idx_audit_stmt FROM @idx_audit_sql; EXECUTE idx_audit_stmt; DEALLOCATE PREPARE idx_audit_stmt;

-- ============================================================
-- FIX 4C: Fix audit_log JSON columns (TEXT -> JSON type)
-- ============================================================

SET @audit_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'connectit_db' AND TABLE_NAME = 'audit_log'
);

SET @alter_audit = IF(@audit_exists > 0,
    'ALTER TABLE audit_log MODIFY COLUMN old_values JSON NULL, MODIFY COLUMN new_values JSON NULL',
    'SELECT ''audit_log table not found, skipping JSON fix'' AS info'
);
PREPARE alter_audit_stmt FROM @alter_audit; EXECUTE alter_audit_stmt; DEALLOCATE PREPARE alter_audit_stmt;

-- ============================================================
-- FIX 4D: Add FK constraint to user_sessions
-- user_sessions.user_id -> users.uid ON DELETE CASCADE
-- ============================================================

SET @us_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'connectit_db' AND TABLE_NAME = 'user_sessions'
);

SET @us_fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA    = 'connectit_db'
      AND TABLE_NAME      = 'user_sessions'
      AND CONSTRAINT_NAME = 'fk_session_user'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @fk_us = IF(@us_exists > 0 AND @us_fk_exists = 0,
    'ALTER TABLE user_sessions ADD CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(uid) ON DELETE CASCADE',
    'SELECT ''user_sessions FK skipped (table missing or FK exists)'' AS info'
);
PREPARE fk_us_stmt FROM @fk_us; EXECUTE fk_us_stmt; DEALLOCATE PREPARE fk_us_stmt;

-- ============================================================
-- FIX 4E: Add FK constraint to company_feature_permissions
-- company_feature_permissions.company_id -> users.uid ON DELETE CASCADE
-- ============================================================

SET @cfp_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'connectit_db' AND TABLE_NAME = 'company_feature_permissions'
);

SET @cfp_fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA    = 'connectit_db'
      AND TABLE_NAME      = 'company_feature_permissions'
      AND CONSTRAINT_NAME = 'fk_feature_company'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @fk_cfp = IF(@cfp_exists > 0 AND @cfp_fk_exists = 0,
    'ALTER TABLE company_feature_permissions ADD CONSTRAINT fk_feature_company FOREIGN KEY (company_id) REFERENCES users(uid) ON DELETE CASCADE',
    'SELECT ''company_feature_permissions FK skipped (table missing or FK exists)'' AS info'
);
PREPARE fk_cfp_stmt FROM @fk_cfp; EXECUTE fk_cfp_stmt; DEALLOCATE PREPARE fk_cfp_stmt;

-- ============================================================
-- Done. Verify with:
--   SHOW INDEX FROM tickets;
--   SHOW CREATE TABLE tickets\G
-- ============================================================
SELECT 'Ticklora DB hardening migration (FIX 4) completed successfully.' AS migration_status;
