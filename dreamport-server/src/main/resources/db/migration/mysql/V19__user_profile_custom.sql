-- 个人主页自定义扩展:背景图/横幅图/主题色/自定义 CSS
ALTER TABLE dp_user_profile ADD COLUMN bg_image    VARCHAR(255) NULL;  -- 背景图 URL(/uploads/ 上传)
ALTER TABLE dp_user_profile ADD COLUMN banner_image VARCHAR(255) NULL;  -- 横幅图 URL(优先于主题色渐变)
ALTER TABLE dp_user_profile ADD COLUMN accent      VARCHAR(16)  NULL;  -- 主题色 #rrggbb(覆盖预设横幅色)
ALTER TABLE dp_user_profile ADD COLUMN css         TEXT         NULL;  -- 自定义 CSS(保存时消毒+作用域前缀,≤8000 字符)
