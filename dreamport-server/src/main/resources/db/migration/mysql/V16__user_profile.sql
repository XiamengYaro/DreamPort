-- 玩家个人主页自定义(简介/横幅主题色/社交链接;公开资料页与 /api/players/profile 联动)
CREATE TABLE dp_user_profile (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(32)  NOT NULL,
    bio          VARCHAR(500) NULL,              -- 个人简介(纯文本,渲染时转义)
    banner       VARCHAR(16)  NULL,              -- 横幅预设色键(amber/rose/sky/emerald/violet/stone)
    social_links TEXT         NULL,              -- JSON 数组:[{label,url}](≤5 条,url 仅 http/https)
    updated_at   BIGINT       NOT NULL,
    UNIQUE KEY uk_user_profile (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
