# 后端 · 数据库与定时任务

## 数据表(dreamport 库,前缀 `dp_`)

| 表 | 内容 |
|---|---|
| `dp_user` | 用户主表:账号/邮箱/状态/密码(algo+hash)/问卷/验证(Java+基岩+Microsoft)/QQ/封禁(banReason/banTime/**banUntil**)/头像 |
| `dp_setting` | 业务设置 KV(skey/svalue/updated_at/updated_by),管理后台热生效 |
| `dp_chat_message` | 聊天消息(origin=game/web/qq、player、server_id),保留 7 天+5 万条 |
| `dp_photo_comment` | 照片墙留言(photo_key/content/approved) |
| `dp_server` | 服务器注册表(心跳 upsert:serverId/名称/版本/在线/max/玩家列表) |
| `dp_online_history` | 在线人数历史(5 分钟采样,保留 7 天) |
| `dp_audit` | 审计日志 |
| 问卷/邀请/申诉/通知/迁移报告等 | 各业务表(见 API_CONTRACT.md 附录) |
| `flyway_schema_history` | 迁移记录 |

## Flyway 迁移

启动自动执行(`classpath:db/migration/mysql`),**禁止手改历史脚本**:

| 版本 | 内容 |
|---|---|
| V1 | 基础表(dp_user/dp_setting/dp_server 等) |
| V2 | 核心业务表(问卷/审核/通知等) |
| V3 | 聊天与服务器信息(dp_chat_message/dp_server 扩列/dp_online_history) |
| V4 | 照片墙留言 |
| V5 | 临时封禁(ban_until) |
| V6 | 留言审核(approved) |

> 迁移失败处理:`DROP` 半建表 + `DELETE FROM flyway_schema_history WHERE version='N' AND success=0` 再重启。

## 定时任务(7 个)

| 任务 | 周期 | 类 | 说明 |
|---|---|---|---|
| 邮箱验证码清扫 | 60s | VerifyCodeService | 清理过期验证码 |
| 图形验证码清扫 | 5min | CaptchaService | 清理过期 captcha |
| ID 验证窗口清扫 | 5min | MinecraftVerifyService | 清理过期进服记录(3 分钟窗口) |
| **在线采样 + 离线告警** | 5min | ServerStatsService | 心跳判定服务器上下线(离线→通知管理员+邮件)、写入 dp_online_history |
| **聊天历史清理** | 1h | ChatService | 删除 7 天前记录,总量超 5 万条裁剪 |
| **QQ 验证码清扫** | 1min | BindCodeService | 清理过期/已用验证码 |
| **临时封禁自动解封** | 1h | ReviewService.unbanExpired | banUntil 到期→解封+审计+通知 |

## 邮件模板(外置)

- 内置模板打包在 JAR;工作目录 `email/` 下放置同名模板可**覆盖**(升级不丢失)
- 现有模板:验证码、欢迎、审核结果、封禁/解封(account_banned/unbanned)、管理员通知等,分 `*_zh` / `*_en`
- SMTP 未配置时为**日志模式**:内容打印到日志(验证码排障常用:`grep 验证码已生成`)

## WebSocket 推送(18899)

实时推送:聊天新消息、服务器状态变更、在线人数更新等。前端聊天室/状态页消费;SSE(`/api/astrbot/stream`)为 QQ 桥下行通道,与 WS 相互独立。
