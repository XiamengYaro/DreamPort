-- 通知铃铛功能整体下线(前端铃铛已移除,通知改为纯邮件通道)
-- 邮件通知不受影响:审核结果/封禁/工单回复/服务器告警等仍走 MailService
DROP TABLE IF EXISTS dp_notification;
