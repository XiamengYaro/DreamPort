-- 两步验证(2FA):TOTP + 恢复码 + 邮箱备用通道
-- secret 存 Base32;recovery_hashes 为 JSON 数组(bcrypt,单次使用);邮箱备用码为内存态不落库
CREATE TABLE dp_user_2fa (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(32) NOT NULL,
    secret         VARCHAR(64) NOT NULL,
    enabled        TINYINT     NOT NULL DEFAULT 0,   -- 0=待验证绑定中 | 1=已启用
    recovery_hashes TEXT       NULL,
    created_at     BIGINT      NOT NULL,
    verified_at    BIGINT      NULL,
    UNIQUE KEY uk_user_2fa (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
