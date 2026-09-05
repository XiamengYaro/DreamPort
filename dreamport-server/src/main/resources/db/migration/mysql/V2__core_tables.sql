-- DreamPort v0.2.0 业务表（MySQL）
-- 邀请 / 站内通知 / 待登录记录 / 密码重置 / 申诉 / 村民族谱 / 公共机器 / 问卷题库

CREATE TABLE dp_invite (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(32) NOT NULL,
    inviter_username VARCHAR(64) NOT NULL,
    invitee_username VARCHAR(64) NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at       BIGINT NOT NULL,
    expires_at       BIGINT NOT NULL,
    used_at          BIGINT NULL,
    CONSTRAINT uk_invite_code UNIQUE (code),
    INDEX idx_invite_inviter (inviter_username),
    INDEX idx_invite_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_notification (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    username         VARCHAR(64) NOT NULL,
    type             VARCHAR(32) NOT NULL,
    title            VARCHAR(255) NOT NULL,
    message          TEXT NULL,
    is_read          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       BIGINT NOT NULL,
    related_username VARCHAR(64) NULL,
    INDEX idx_notif_username (username),
    INDEX idx_notif_unread (username, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_pending_login (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    minecraft_name VARCHAR(64) NOT NULL,
    minecraft_uuid VARCHAR(64) NOT NULL,
    ip_address     VARCHAR(45) NULL,
    login_time     BIGINT NOT NULL,
    verified       BOOLEAN NOT NULL DEFAULT FALSE,
    expire_time    BIGINT NOT NULL DEFAULT 0,
    INDEX idx_plogin_name (minecraft_name),
    INDEX idx_plogin_uuid (minecraft_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_password_reset (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64) NOT NULL,
    token      VARCHAR(64) NOT NULL,
    expires_at BIGINT NOT NULL,
    used       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    CONSTRAINT uk_preset_token UNIQUE (token),
    INDEX idx_preset_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_appeal (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64) NOT NULL,
    reason      TEXT NULL,
    status      VARCHAR(32) NOT NULL DEFAULT 'pending',
    admin_reply TEXT NULL,
    created_at  BIGINT NOT NULL,
    resolved_at BIGINT NULL,
    resolved_by VARCHAR(64) NULL,
    INDEX idx_appeal_username (username),
    INDEX idx_appeal_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_village_trade (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_name VARCHAR(64) NOT NULL,
    world       VARCHAR(32) NOT NULL,
    x           INT NOT NULL,
    y           INT NOT NULL,
    z           INT NOT NULL,
    item_input  VARCHAR(128) NULL,
    item_output VARCHAR(128) NULL,
    price       DECIMAL(10,2) NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at  BIGINT NOT NULL,
    reviewed_by VARCHAR(64) NULL,
    reviewed_at BIGINT NULL,
    INDEX idx_vtrade_status (status),
    INDEX idx_vtrade_player (player_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_public_machine (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(128) NOT NULL,
    type           VARCHAR(32) NULL,
    world          VARCHAR(32) NOT NULL,
    x              INT NOT NULL,
    y              INT NOT NULL,
    z              INT NOT NULL,
    builder        VARCHAR(64) NULL,
    usage_text     TEXT NULL,
    screenshot_url VARCHAR(256) NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'pending',
    submitter      VARCHAR(64) NOT NULL,
    created_at     BIGINT NOT NULL,
    reviewed_by    VARCHAR(64) NULL,
    reviewed_at    BIGINT NULL,
    INDEX idx_machine_status (status),
    INDEX idx_machine_submitter (submitter)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_questionnaire (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    pass_score INT NOT NULL DEFAULT 60,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_question (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    questionnaire_id BIGINT NOT NULL,
    question_zh      TEXT NOT NULL,
    question_en      TEXT NULL,
    type             VARCHAR(16) NOT NULL,
    required         BOOLEAN NOT NULL DEFAULT TRUE,
    max_score        INT NOT NULL DEFAULT 0,
    scoring_rule     TEXT NULL,
    multiline        BOOLEAN NOT NULL DEFAULT FALSE,
    min_length       INT NULL,
    max_length       INT NULL,
    min_selections   INT NULL,
    max_selections   INT NULL,
    placeholder_zh   VARCHAR(255) NULL,
    placeholder_en   VARCHAR(255) NULL,
    sort_order       INT NOT NULL DEFAULT 0,
    INDEX idx_question_q (questionnaire_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_question_option (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    text_zh     VARCHAR(255) NOT NULL,
    text_en     VARCHAR(255) NULL,
    score       INT NOT NULL DEFAULT 0,
    sort_order  INT NOT NULL DEFAULT 0,
    INDEX idx_option_question (question_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
