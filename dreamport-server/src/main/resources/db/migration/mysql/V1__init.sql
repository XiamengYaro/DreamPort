-- DreamPort v0.1.0 基础表（MySQL）
-- 规则：dp_ 前缀 / 主键 BIGINT AUTO_INCREMENT / 时间戳 BIGINT 毫秒 / utf8mb4（Rules.md §4）

CREATE TABLE dp_user (
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username                      VARCHAR(64) NOT NULL,
    email                         VARCHAR(255) NULL,
    status                        VARCHAR(32) NOT NULL DEFAULT 'pending',
    password_algo                 VARCHAR(16) NOT NULL DEFAULT 'bcrypt',
    password_hash                 VARCHAR(255) NOT NULL,
    reg_time                      BIGINT NOT NULL,
    discord_id                    VARCHAR(64) NULL,
    qq_number                     VARCHAR(32) NULL,
    qq_bound_at                   BIGINT NULL,
    questionnaire_score           INT NOT NULL DEFAULT 0,
    questionnaire_passed          BOOLEAN NOT NULL DEFAULT FALSE,
    questionnaire_review_summary  TEXT NULL,
    questionnaire_scored_at       BIGINT NULL,
    questionnaire_reasons         TEXT NULL,
    questionnaire_answers         TEXT NULL,
    minecraft_uuid                VARCHAR(64) NULL,
    minecraft_name                VARCHAR(64) NULL,
    microsoft_verified            BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at                   BIGINT NULL,
    verify_type                   VARCHAR(32) NULL,
    invited_by                    VARCHAR(64) NULL,
    bedrock_uuid                  VARCHAR(64) NULL,
    bedrock_name                  VARCHAR(64) NULL,
    bedrock_verified              BOOLEAN NOT NULL DEFAULT FALSE,
    bedrock_verified_at           BIGINT NULL,
    ban_reason                    VARCHAR(512) NULL,
    ban_time                      BIGINT NULL,
    avatar                        VARCHAR(255) NULL,
    CONSTRAINT uk_user_username UNIQUE (username),
    INDEX idx_user_email (email),
    INDEX idx_user_status (status),
    INDEX idx_user_minecraft_name (minecraft_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_audit_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    action     VARCHAR(64) NOT NULL,
    operator   VARCHAR(64) NOT NULL,
    target     VARCHAR(64) NULL,
    detail     TEXT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_audit_action (action),
    INDEX idx_audit_target (target),
    INDEX idx_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_setting (
    skey       VARCHAR(64) PRIMARY KEY,
    svalue     TEXT NULL,
    updated_at BIGINT NOT NULL,
    updated_by VARCHAR(64) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_server (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    server_id      VARCHAR(64) NOT NULL,
    name           VARCHAR(128) NULL,
    role           VARCHAR(16) NOT NULL DEFAULT 'secondary',
    token_hash     VARCHAR(255) NOT NULL,
    last_heartbeat BIGINT NULL,
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_server_server_id UNIQUE (server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
