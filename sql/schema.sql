CREATE DATABASE IF NOT EXISTS knowledge_kb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE knowledge_kb;

CREATE TABLE IF NOT EXISTS kb_document (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    title         VARCHAR(255)  NOT NULL,
    object_key    VARCHAR(512)  NOT NULL,
    status        VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    chunk_count   INT           NOT NULL DEFAULT 0,
    error_msg     VARCHAR(1000) NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_chunk (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_id         BIGINT        NOT NULL,
    chunk_index    INT           NOT NULL,
    content        MEDIUMTEXT    NOT NULL,
    token_estimate INT           NOT NULL DEFAULT 0,
    milvus_id      BIGINT        NOT NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id),
    INDEX idx_milvus_id (milvus_id),
    CONSTRAINT fk_chunk_doc FOREIGN KEY (doc_id) REFERENCES kb_document (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_chat_session (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    title       VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_chat_message (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT       NOT NULL,
    role        VARCHAR(32)  NOT NULL,
    content     MEDIUMTEXT   NOT NULL,
    citations   JSON         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    CONSTRAINT fk_msg_session FOREIGN KEY (session_id) REFERENCES kb_chat_session (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
