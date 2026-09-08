-- 成就与称号系统:拥有记录/当前佩戴/成就进度
CREATE TABLE dp_user_titles (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(32) NOT NULL,
    title_code  VARCHAR(32) NOT NULL,
    obtained_at BIGINT      NOT NULL,
    UNIQUE KEY uk_user_title (username, title_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_title_equipped (
    username   VARCHAR(32) NOT NULL PRIMARY KEY,
    title_code VARCHAR(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_achievement_progress (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(32) NOT NULL,
    achievement_id VARCHAR(64) NOT NULL,
    progress       INT         NOT NULL DEFAULT 0,
    completed      TINYINT     NOT NULL DEFAULT 0,
    completed_at   BIGINT      NULL,
    UNIQUE KEY uk_ach_progress (username, achievement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
