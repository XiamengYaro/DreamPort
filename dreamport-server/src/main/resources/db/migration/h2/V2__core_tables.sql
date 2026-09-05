-- DreamPort v0.2.0 业务表（H2 / MySQL 兼容模式，与 mysql/V2 同构）

CREATE TABLE dp_invite (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(32) NOT NULL,
    inviter_username VARCHAR(64) NOT NULL,
    invitee_username VARCHAR(64) NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at       BIGINT NOT NULL,
    expires_at       BIGINT NOT NULL,
    used_at          BIGINT NULL,
    CONSTRAINT uk_invite_code UNIQUE (code)
);
CREATE INDEX idx_invite_inviter ON dp_invite (inviter_username);
CREATE INDEX idx_invite_status ON dp_invite (status);

CREATE TABLE dp_notification (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    username         VARCHAR(64) NOT NULL,
    type             VARCHAR(32) NOT NULL,
    title            VARCHAR(255) NOT NULL,
    message          TEXT NULL,
    is_read          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       BIGINT NOT NULL,
    related_username VARCHAR(64) NULL
);
CREATE INDEX idx_notif_username ON dp_notification (username);
CREATE INDEX idx_notif_unread ON dp_notification (username, is_read);

CREATE TABLE dp_pending_login (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    minecraft_name VARCHAR(64) NOT NULL,
    minecraft_uuid VARCHAR(64) NOT NULL,
    ip_address     VARCHAR(45) NULL,
    login_time     BIGINT NOT NULL,
    verified       BOOLEAN NOT NULL DEFAULT FALSE,
    expire_time    BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_plogin_name ON dp_pending_login (minecraft_name);
CREATE INDEX idx_plogin_uuid ON dp_pending_login (minecraft_uuid);

CREATE TABLE dp_password_reset (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64) NOT NULL,
    token      VARCHAR(64) NOT NULL,
    expires_at BIGINT NOT NULL,
    used       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    CONSTRAINT uk_preset_token UNIQUE (token)
);
CREATE INDEX idx_preset_username ON dp_password_reset (username);

CREATE TABLE dp_appeal (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64) NOT NULL,
    reason      TEXT NULL,
    status      VARCHAR(32) NOT NULL DEFAULT 'pending',
    admin_reply TEXT NULL,
    created_at  BIGINT NOT NULL,
    resolved_at BIGINT NULL,
    resolved_by VARCHAR(64) NULL
);
CREATE INDEX idx_appeal_username ON dp_appeal (username);
CREATE INDEX idx_appeal_status ON dp_appeal (status);

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
    reviewed_at BIGINT NULL
);
CREATE INDEX idx_vtrade_status ON dp_village_trade (status);
CREATE INDEX idx_vtrade_player ON dp_village_trade (player_name);

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
    reviewed_at    BIGINT NULL
);
CREATE INDEX idx_machine_status ON dp_public_machine (status);
CREATE INDEX idx_machine_submitter ON dp_public_machine (submitter);

CREATE TABLE dp_questionnaire (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    pass_score INT NOT NULL DEFAULT 60,
    created_at BIGINT NOT NULL
);

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
    sort_order       INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_question_q ON dp_question (questionnaire_id, sort_order);

CREATE TABLE dp_question_option (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    text_zh     VARCHAR(255) NOT NULL,
    text_en     VARCHAR(255) NULL,
    score       INT NOT NULL DEFAULT 0,
    sort_order  INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_option_question ON dp_question_option (question_id, sort_order);
