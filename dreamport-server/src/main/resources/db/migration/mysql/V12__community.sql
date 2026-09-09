-- 社区与内容:反馈工单(多轮对话式) + 投票 + 论坛(进阶版)
-- 反馈工单:玩家提交会话 → 管理员回复(铃铛+邮件) → 玩家可追问 → 关闭
CREATE TABLE dp_feedback (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(32)  NOT NULL,
    category    VARCHAR(16)  NOT NULL DEFAULT 'other',  -- suggestion=建议 | bug=问题反馈 | other=其他
    title       VARCHAR(128) NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'open',   -- open=待处理 | answered=已回复 | closed=已关闭
    created_at  BIGINT       NOT NULL,
    updated_at  BIGINT       NULL,
    KEY idx_feedback_user (username),
    KEY idx_feedback_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_feedback_message (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    feedback_id  BIGINT      NOT NULL,
    sender_role  VARCHAR(8)  NOT NULL,                    -- player | admin
    sender       VARCHAR(32) NOT NULL,
    content      TEXT        NOT NULL,
    created_at   BIGINT      NOT NULL,
    KEY idx_feedback_msg (feedback_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 投票:单选/多选,结果可见性逐场可配(默认实时)
CREATE TABLE dp_poll (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    title             VARCHAR(128) NOT NULL,
    description       TEXT         NULL,
    multiple          TINYINT      NOT NULL DEFAULT 0,       -- 1=多选
    result_visibility VARCHAR(8)   NOT NULL DEFAULT 'open',  -- open=实时可见 | ended=结束后可见
    status            VARCHAR(16)  NOT NULL DEFAULT 'open',  -- draft | open | closed
    ends_at           BIGINT       NULL,                     -- 到期自动视为结束
    created_by        VARCHAR(32)  NOT NULL,
    created_at        BIGINT       NOT NULL,
    KEY idx_poll_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_poll_option (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id  BIGINT      NOT NULL,
    label    VARCHAR(128) NOT NULL,
    sort     INT         NOT NULL DEFAULT 0,
    KEY idx_poll_option (poll_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_poll_vote (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id    BIGINT      NOT NULL,
    option_id  BIGINT      NOT NULL,
    username   VARCHAR(32) NOT NULL,
    created_at BIGINT      NOT NULL,
    -- 多选场景同一用户多行;单选由服务端先删后插保证一行
    UNIQUE KEY uk_poll_vote (poll_id, username, option_id),
    KEY idx_poll_user (poll_id, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 论坛(进阶版):板块/帖子/回复/点赞;@提及解析正文不建表
CREATE TABLE dp_forum_section (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255) NULL,
    sort        INT          NOT NULL DEFAULT 0,
    locked      TINYINT      NOT NULL DEFAULT 0,          -- 1=只读(仅管理员可发)
    created_at  BIGINT       NOT NULL,
    UNIQUE KEY uk_forum_section_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_forum_thread (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    section_id    BIGINT       NOT NULL,
    username      VARCHAR(32)  NOT NULL,
    title         VARCHAR(128) NOT NULL,
    content       MEDIUMTEXT   NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'published', -- pending|published|rejected|deleted
    pinned        TINYINT      NOT NULL DEFAULT 0,
    essence       TINYINT      NOT NULL DEFAULT 0,
    locked        TINYINT      NOT NULL DEFAULT 0,           -- 1=锁定不可回复
    reply_count   INT          NOT NULL DEFAULT 0,
    like_count    INT          NOT NULL DEFAULT 0,
    last_reply_at BIGINT       NULL,
    edited        TINYINT      NOT NULL DEFAULT 0,           -- 作者编辑留痕
    reviewed_by   VARCHAR(32)  NULL,
    reviewed_at   BIGINT       NULL,
    created_at    BIGINT       NOT NULL,
    updated_at    BIGINT       NULL,
    KEY idx_thread_section (section_id, status, pinned),
    KEY idx_thread_user (username),
    KEY idx_thread_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_forum_reply (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    thread_id  BIGINT      NOT NULL,
    username   VARCHAR(32) NOT NULL,
    content    TEXT        NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'published', -- published|pending|deleted
    like_count INT         NOT NULL DEFAULT 0,
    created_at BIGINT      NOT NULL,
    KEY idx_reply_thread (thread_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dp_forum_like (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type VARCHAR(8)  NOT NULL,                     -- thread | reply
    target_id   BIGINT      NOT NULL,
    username    VARCHAR(32) NOT NULL,
    created_at  BIGINT      NOT NULL,
    UNIQUE KEY uk_forum_like (target_type, target_id, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
