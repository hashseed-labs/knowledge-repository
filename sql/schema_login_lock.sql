-- Incremental: login fail lock (3 attempts / calendar day).
-- Existing installs: run this once. Ignore "Duplicate column" if already applied.
USE knowledge_kb;

ALTER TABLE sys_user
    ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0 AFTER status;

ALTER TABLE sys_user
    ADD COLUMN failed_login_day DATE NULL AFTER failed_login_count;
