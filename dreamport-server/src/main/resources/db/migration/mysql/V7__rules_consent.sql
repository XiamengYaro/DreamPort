-- 注册后强制阅读服务器守则:同意记录(每用户一条,username 主键)
CREATE TABLE dp_rules_consent (
    username    VARCHAR(32) NOT NULL,
    accepted_at BIGINT      NOT NULL,
    PRIMARY KEY (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
