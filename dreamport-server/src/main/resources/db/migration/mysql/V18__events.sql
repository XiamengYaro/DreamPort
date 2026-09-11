-- 活动日历:管理员创建活动,玩家报名
CREATE TABLE dp_event (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(128) NOT NULL,
    description TEXT         NULL,
    event_at    BIGINT       NOT NULL,             -- 活动开始时间(毫秒)
    created_by  VARCHAR(32)  NOT NULL,
    created_at  BIGINT       NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_event_signup (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id   BIGINT      NOT NULL,
    username   VARCHAR(32) NOT NULL,
    created_at BIGINT      NOT NULL,
    UNIQUE KEY uk_event_signup (event_id, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
