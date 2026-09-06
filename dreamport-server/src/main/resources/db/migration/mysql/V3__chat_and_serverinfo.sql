-- DreamPort v1.1 消息历史与服务器信息落库（docs/CHAT_SERVERINFO_PLAN.md §2.1）
-- 聊天消息（game/web/qq/system 四源，7 天保留 + 5 万条硬顶）
-- 服务器心跳快照复用 V1 的 dp_server 注册表（此前未启用，ALTER 补齐快照列）
-- 在线人数采样（5 分钟粒度，7 天保留）

CREATE TABLE dp_chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    origin      VARCHAR(16)  NOT NULL,
    player      VARCHAR(64)  NOT NULL,
    message     VARCHAR(512) NOT NULL,
    server_id   VARCHAR(64)  NULL,
    created_at  BIGINT       NOT NULL,
    INDEX idx_chat_created (created_at),
    INDEX idx_chat_origin (origin, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- dp_server 已有列：id / server_id(UNIQUE) / name / role / token_hash / last_heartbeat / enabled
ALTER TABLE dp_server
    ADD COLUMN version        VARCHAR(32) NULL AFTER role,
    ADD COLUMN online_players INT NOT NULL DEFAULT 0,
    ADD COLUMN max_players    INT NOT NULL DEFAULT 0;

CREATE TABLE dp_online_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    server_id   VARCHAR(64) NOT NULL,
    online      INT NOT NULL,
    max_players INT NOT NULL,
    sampled_at  BIGINT NOT NULL,
    INDEX idx_online_time (sampled_at),
    INDEX idx_online_server (server_id, sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
