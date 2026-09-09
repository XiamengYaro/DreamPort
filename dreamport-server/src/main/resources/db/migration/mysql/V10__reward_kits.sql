-- 奖励礼包:模板(采集的背包物品+附加指令) + 邮件快照列
-- 模板:Web 创建(capturing)→ 服内管理员 /xmw kit save <名> 采集背包上传(ready)→ 后台发放时快照进 dp_mail
CREATE TABLE dp_reward_kit (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    note        VARCHAR(255) NULL,
    items       MEDIUMTEXT NULL,          -- JSON:[{"s":"<base64 ItemStack>","n":"展示名","c":数量}]
    commands    TEXT NULL,                -- 附加指令:[{"type":"command","cmd":"..."}](同商店格式)
    summary     VARCHAR(255) NULL,        -- 内容概要(后台列表展示)
    status      VARCHAR(16)  NOT NULL DEFAULT 'capturing',  -- capturing=待采集 / ready=可发放 / disabled=停用
    captured_by VARCHAR(32) NULL,
    captured_at BIGINT NULL,
    created_by  VARCHAR(32) NOT NULL,
    created_at  BIGINT      NOT NULL,
    updated_at  BIGINT      NULL,
    UNIQUE KEY uk_kit_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 邮件快照列:发放时把礼包物品复制进邮件行(自包含,发放后改模板不影响已发邮件)
-- items 非空 → 插件领取时物品直发(先给物品再执行 commands);为空 → 走原指令逻辑(向后兼容)
ALTER TABLE dp_mail ADD COLUMN items MEDIUMTEXT NULL AFTER commands;
