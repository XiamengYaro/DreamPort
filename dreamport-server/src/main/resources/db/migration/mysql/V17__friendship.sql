-- 好友系统:双向去重的申请/同意模型
-- status: pending(待对方处理) | accepted(已同意);拒绝/删除 = 直接删行
CREATE TABLE dp_friendship (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester   VARCHAR(32) NOT NULL,              -- 发起方
    addressee   VARCHAR(32) NOT NULL,              -- 接收方
    status      VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at  BIGINT      NOT NULL,
    responded_at BIGINT     NULL,
    UNIQUE KEY uk_friend_pair (requester, addressee)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
