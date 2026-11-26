-- Incremental migration for MVP -> Enterprise RBAC.
-- Fresh install: use schema.sql instead.
USE knowledge_kb;

CREATE TABLE IF NOT EXISTS sys_user (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    username            VARCHAR(64)  NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    display_name        VARCHAR(128) NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_count  INT          NOT NULL DEFAULT 0,
    failed_login_day    DATE         NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_tenant (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    code       VARCHAR(64)  NOT NULL UNIQUE,
    name       VARCHAR(128) NOT NULL,
    status     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user_tenant (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT  NOT NULL,
    tenant_id  BIGINT  NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_tenant (user_id, tenant_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_permission (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    code       VARCHAR(128) NOT NULL UNIQUE,
    name       VARCHAR(128) NOT NULL,
    module     VARCHAR(64)  NOT NULL,
    type       VARCHAR(32)  NOT NULL DEFAULT 'API',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id  BIGINT       NOT NULL DEFAULT 0,
    code       VARCHAR(64)  NOT NULL,
    name       VARCHAR(128) NOT NULL,
    builtin    TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_role (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_perm (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user_role (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    tenant_id  BIGINT NOT NULL,
    role_id    BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_tenant_role (user_id, tenant_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_refresh_token (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(128) NOT NULL UNIQUE,
    expires_at DATETIME     NOT NULL,
    revoked    TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id     BIGINT       NULL,
    user_id       BIGINT       NULL,
    username      VARCHAR(64)  NULL,
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64)  NULL,
    resource_id   VARCHAR(64)  NULL,
    success       TINYINT      NOT NULL DEFAULT 1,
    ip            VARCHAR(64)  NULL,
    user_agent    VARCHAR(512) NULL,
    detail        JSON         NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_time (tenant_id, created_at),
    INDEX idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_space (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id   BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_member (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id      BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    role_id    BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_kb_user (kb_id, user_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Add isolation columns if missing (ignore duplicate column errors when re-run)
-- Run these manually once on MVP DB:
-- ALTER TABLE kb_document ADD COLUMN tenant_id BIGINT NULL AFTER id;
-- ALTER TABLE kb_document ADD COLUMN kb_id BIGINT NULL AFTER tenant_id;
-- ALTER TABLE kb_document ADD COLUMN created_by BIGINT NULL AFTER kb_id;
-- ALTER TABLE kb_chunk ADD COLUMN tenant_id BIGINT NULL AFTER id;
-- ALTER TABLE kb_chunk ADD COLUMN kb_id BIGINT NULL AFTER tenant_id;
-- ALTER TABLE kb_chat_session ADD COLUMN tenant_id BIGINT NULL AFTER id;
-- ALTER TABLE kb_chat_session ADD COLUMN kb_id BIGINT NULL AFTER tenant_id;
-- ALTER TABLE kb_chat_session ADD COLUMN user_id BIGINT NULL AFTER kb_id;
