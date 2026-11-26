-- Online exam tables (document -> generated paper). Apply on existing knowledge_kb.
USE knowledge_kb;

CREATE TABLE IF NOT EXISTS exam_paper (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id    BIGINT       NOT NULL,
    kb_id        BIGINT       NOT NULL,
    title        VARCHAR(256) NOT NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    config_json  TEXT         NULL,
    source_doc_ids TEXT       NULL,
    question_count INT        NOT NULL DEFAULT 0,
    total_score  DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_by   BIGINT       NULL,
    error_msg    VARCHAR(1024) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_exam_paper_tenant_kb (tenant_id, kb_id),
    INDEX idx_exam_paper_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exam_question (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id        BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL,
    kb_id           BIGINT       NOT NULL,
    q_type          VARCHAR(32)  NOT NULL,
    stem            TEXT         NOT NULL,
    options_json    TEXT         NULL,
    answer_json     TEXT         NOT NULL,
    analysis_text   TEXT         NULL,
    score           DECIMAL(10,2) NOT NULL DEFAULT 1,
    source_doc_ids  VARCHAR(512) NULL,
    source_chunk_ids VARCHAR(512) NULL,
    sort_no         INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_exam_question_paper (paper_id),
    INDEX idx_exam_question_tenant_kb (tenant_id, kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Reserved for P1/P2 (online attempt + grading); create empty shells for forward compat.
CREATE TABLE IF NOT EXISTS exam_attempt (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id     BIGINT       NOT NULL,
    tenant_id    BIGINT       NOT NULL,
    kb_id        BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'IN_PROGRESS',
    score        DECIMAL(10,2) NULL,
    max_score    DECIMAL(10,2) NULL,
    duration_minutes INT      NULL,
    expires_at   DATETIME     NULL,
    started_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at DATETIME     NULL,
    INDEX idx_exam_attempt_paper_user (paper_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exam_answer (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    attempt_id   BIGINT       NOT NULL,
    question_id  BIGINT       NOT NULL,
    answer_json  TEXT         NULL,
    correct      TINYINT      NULL,
    score        DECIMAL(10,2) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_attempt_question (attempt_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
