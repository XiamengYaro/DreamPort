-- DreamPort v1.1.x 临时封禁支持:ban_until 为空 = 永久封禁;到期由系统自动解封

ALTER TABLE dp_user ADD COLUMN ban_until BIGINT NULL;
