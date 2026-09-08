-- 积分任务系统:余额/流水/任务进度/签到/游戏内邮件
CREATE TABLE dp_points_balance (
    username    VARCHAR(32) NOT NULL PRIMARY KEY,
    balance     INT         NOT NULL DEFAULT 0,
    updated_at  BIGINT      NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_points_ledger (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(32) NOT NULL,
    delta         INT         NOT NULL,
    balance_after INT         NOT NULL,
    source        VARCHAR(32) NOT NULL,
    ref           VARCHAR(64) NULL,
    note          VARCHAR(255) NULL,
    created_at    BIGINT      NOT NULL,
    KEY idx_ledger_user (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_task_progress (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(32) NOT NULL,
    task_id    VARCHAR(64) NOT NULL,
    period_key VARCHAR(16) NOT NULL,
    progress   INT         NOT NULL DEFAULT 0,
    completed  TINYINT     NOT NULL DEFAULT 0,
    claimed    TINYINT     NOT NULL DEFAULT 0,
    claimed_at BIGINT      NULL,
    UNIQUE KEY uk_task_progress (username, task_id, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_signin (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(32) NOT NULL,
    sign_date  VARCHAR(10) NOT NULL,
    source     VARCHAR(8)  NOT NULL,
    created_at BIGINT      NOT NULL,
    UNIQUE KEY uk_signin (username, sign_date, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_mail (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(32) NOT NULL,
    title      VARCHAR(128) NOT NULL,
    commands   TEXT         NOT NULL,
    note       VARCHAR(255) NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'pending',
    created_at BIGINT       NOT NULL,
    claimed_at BIGINT       NULL,
    KEY idx_mail_user (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_daily_activity (
    username        VARCHAR(32) NOT NULL,
    activity_date   VARCHAR(10) NOT NULL,
    playtime_seconds BIGINT     NOT NULL DEFAULT 0,
    login_count     INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (username, activity_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
