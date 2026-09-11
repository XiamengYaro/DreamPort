# DreamPort API 契约

> 本表从旧版 `ApiRouter.java`（80+ context）与 `api.ts`（80+ 方法）逐字冻结（P0 基础），并持续补充 v1.1–v1.4 新增端点，是 `/api/**` 契约的权威清单。
> 规则（Rules.md §3）：路径/方法/请求响应结构与旧版**逐字保持**；有意修复标 `FIX(legacy)`。
> 鉴权图例：🌐 公开 ｜ 🔒 Bearer 用户 ｜ 👑 Bearer 管理员 ｜ 🖥 X-Server-Token ｜ 🤖 X-API-Token ｜ ⚡ 限流
> "落位"= 实现阶段（对应 IMPLEMENTATION_PROGRESS.md）。

## 1. 公开端点 🌐

| 端点 | 方法 | 功能 | 落位 |
|------|------|------|------|
| `/api/health` | GET | 健康检查（新增，运维用） | ✅ P1 |
| `/api/version` | GET | 版本信息（currentVersion/latestVersion/updateAvailable） | ✅ P1 |
| `/api/config` | GET | 站点公开配置（门户/背景/公告/注册开关） | ✅ P1→P7 |
| `/api/captcha`、`/api/captcha/generate` | POST | 图形验证码 | P3 |
| `/api/verify/send` ⚡ | POST | 发送邮箱验证码 | P3 |
| `/api/register` ⚡ | POST | 注册（含问卷/验证码/邀请码分支） | ✅ P1→P3 |
| `/api/login` ⚡ | POST | 用户登录（状态分支响应） | ✅ P1→P3 |
| `/api/review/status` | GET | 审核状态查询（**FIX(legacy)**：改读 query 参数，旧版读 X-Username 头与前端不匹配） | P3 |
| `/api/docs` | GET | 文档列表/内容 | P7 |
| `/api/questionnaire/config` | GET | 问卷题库 | P3 |
| `/api/questionnaire/submit` 🔒 | POST | 问卷提交（同步） | P3 |
| `/api/questionnaire/stream` 🔒 | POST | 问卷提交（**FIX(legacy)**：统一为带鉴权的 SSE 流，替代旧版无鉴权副本） | P3 |
| `/api/questionnaire/appeal` 🔒 | POST | 问卷申诉 | P3 |
| `/api/server/status` | GET | 服务器状态（多服聚合） | P4 |
| `/api/server/player-history` | GET | 在线人数历史（24h/5min 粒度） | P4 |
| `/api/downloads` | GET | 下载中心 | P7 |
| `/api/chat/history` | GET | 聊天室历史（近 7 天，游标翻页） | ✅ P4 |
| `/api/chat/save` 🔒 | POST | 聊天保存（JWT+本人+限流+敏感词，v1.4 审计修复后非公开） | ✅ P4 |
| `/api/chat/stream-ticket` 🔒 | POST | 聊天 SSE 一次性流票据（v1.4，JWT 不进 URL） | ✅ P4 |
| `/api/auth/forgot-password` ⚡ | POST | 忘记密码（防枚举） | P3 |
| `/api/auth/reset-password` | POST | 重置密码（令牌 1h） | P3 |
| `/api/auth/validate` 🔒 | GET/POST | Token 校验 | ✅ P1 |
| `/api/cmi/stats[/extended]`、`/api/cmi/wealth`、`/api/cmi/playtime`、`/api/cmi/activedays`、`/api/cmi/online`、`/api/cmi/banned`、`/api/cmi/player/:name` | GET | 经济/时长/在线统计（**FIX(legacy)**：真实统计，移除估算值） | P4 |
| `/api/village/list` | GET | 村民族谱（仅 approved） | P4 |
| `/api/machine/list` | GET | 公共机器（仅 approved） | P4 |
| `/api/players/list`、`/api/players/profile/:name` | GET | 玩家目录/档案（含当前佩戴称号 title 字段） | P4 |
| `/api/avatar/{name}?size=` | GET | 公开大头照（本地双层渲染，v1.3） | ✅ P4 |

## 2. 用户端点 🔒

| 端点 | 方法 | 功能 | 落位 |
|------|------|------|------|
| `/api/user/status`、`/api/user/profile` | GET | 个人状态/资料 | P3 |
| `/api/user/update`、`/api/user/password`、`/api/user/email/update`、`/api/user/avatar/upload` | POST | 资料/改密/换邮箱（验证码）/头像（≤2MB） | P3 |
| `/api/user/minecraft/{set,verify,status}` | POST/GET | Java ID 绑定与进服验证 | P3 |
| `/api/user/bedrock/{set,verify,cancel,status}` | POST/GET | 基岩 ID 绑定/验证/取消 | P3 |
| `/api/verify/{check,status}` | POST/GET | 触发/查询 ID 验证 | P3 |
| `/api/invite/{generate,my-codes,pending,apply,confirm,reject}` | POST/GET | 邀请全流程 | P4 |
| `/api/notifications`、`/api/notifications/read[/read-all]` | GET/POST | 通知 | P4 |
| `/api/village/submit` | POST | 村谱提交 | P4 |
| `/api/machine/{submit,upload}` | POST | 机器提交/截图上传（multipart ≤5MB） | P4 |
| `/api/chat/send`、`/api/chat/stream` | POST/GET | 聊天发送 / SSE 订阅 | P4 |
| `/api/user/rules/accept` | POST | 同意服务器守则（幂等，v1.4；set/verify 前置） | ✅ P3 |
| `/api/user/minecraft/sync-by-uuid` | POST | 按 UUID 同步新 ID（v1.3，仅官方 v4 UUID） | ✅ P3 |
| `/api/user/qq/{status,bind,unbind}` | GET/POST | QQ 绑定（v1.1，验证码强绑定） | ✅ P4 |
| `/api/points/{center,signin,claim,shop,redeem}` | GET/POST | 积分任务（v1.4，签到/任务/兑换） | ✅ P4 |
| `/api/titles/{mine,equip,unequip}` | GET/POST | 称号（v1.4；mine 返回 owned/locked/equipped/achievements） | ✅ P4 |
| `/api/user/profile-custom` | GET/PUT | 主页自定义（v1.6）：`{bio, banner, socialLinks[], bgImage, bannerImage, accent, css}`；css 服务端消毒+`#pc-root` 作用域，≤8000 字符 | ✅ P4 |

## 3. 管理端点 👑

| 端点 | 方法 | 功能 | 落位 |
|------|------|------|------|
| `/api/admin/login` ⚡ | POST | 管理员登录（OP 名单 + 密码） | ✅ P1→P4 |
| `/api/admin/verify` | POST | 管理 token 校验 | P4 |
| `/api/admin/users`、`/api/admin/user/{approve,reject,ban,unban,delete,add,update,update-status}` | GET/POST | 用户管理 | P4 |
| `/api/admin/user/batch-{approve,reject,ban,delete}` | POST | 批量操作 | P4 |
| `/api/admin/user/{set-bedrock,verify-bedrock}` | POST | 代管基岩绑定 | P4 |
| `/api/admin/questionnaire/{list,reset,save,add-question,delete-question}`、`/api/admin/questionnaire/{update,:user}` | GET/POST | 问卷管理/改分 | P3 |
| `/api/admin/appeals[/approve,/reject]` | GET/POST | 申诉处理（**FIX(legacy)**：通过时若用户仍 rejected → 转人工复核队列而非直接 pending_review） | P3 |
| `/api/admin/audits` | GET | 审计日志 | P4 |
| `/api/admin/stats/{overview,registrations,questionnaires}` | GET | 统计 | P4 |
| `/api/admin/{background,portal,portal/team,portal/carousel,portal/features,portal/timeline,server-config,system-config}` | GET/POST | 站点配置（写 dp_setting） | P7 |
| `/api/admin/upload` | POST | 图片上传（multipart ≤5MB，魔数校验） | P7 |
| `/api/admin/maintenance` | GET/POST | 维护模式（持久化） | P4 |
| `/api/admin/export/{users,audits}` | GET | 导出（CSV/JSON） | P7 |
| `/api/admin/settings/{rules,tasksconfig,shopconfig}` | GET/PUT | 守则/任务/兑换商店配置（v1.4） | ✅ P4 |
| `/api/admin/settings/{titlesconfig,achievementsconfig}` | PUT | 称号/成就定义（裸数组，v1.4） | ✅ P4 |
| `/api/admin/titles/{overview,grant,revoke}` | GET/POST | 称号管理（v1.4，grant/revoke 有校验） | ✅ P4 |
| `/api/admin/rewards/{kits,send}`、`/api/admin/rewards/kits/{id}` | GET/POST/PUT/DELETE | 奖励礼包（v1.4，单人/全员发放） | ✅ P4 |
| `/api/admin/mail/targets`、`/api/admin/mail/broadcast` | GET/POST | 邮件群发（v1.6）：targets 返回 `{count, configured}`；broadcast `{subject≤200, content≤20000}`，目标=dp_user 邮箱非空合法小写去重，异步投递 | ✅ P4 |
| `/api/admin/migration/{upload,report}` | POST/GET | 数据迁移（v0.4+） | ✅ P7 |
| `/api/admin/docs/{create,delete,update,category/create,category/delete,reorder}` | POST | 文档管理 | P7 |
| `/api/village/admin/{pending,approve/:id,reject/:id}` | GET/POST | 村谱审核 | P4 |
| `/api/machine/admin/{pending,approve/:id,reject/:id}` | GET/POST | 机器审核 | P4 |

## 4. 服务器间端点 🖥（/internal/v1，X-Server-Id + X-Server-Token）

| 端点 | 方法 | 功能 | 落位 |
|------|------|------|------|
| `/internal/v1/login-check` | POST | 进服校验 → allow/deny + reasonKey + maintenance | ✅ P1 |
| `/internal/v1/login-record` | POST | 登录尝试记录（ID 验证用） | P5 |
| `/internal/v1/heartbeat` | POST | 心跳（在线数/玩家列表，合并旧版 Bridge 协议） | ✅ P1→P5 |
| `/internal/v1/events` | POST | join/quit/chat 事件上报 | P5 |
| `/internal/v1/economy/snapshot` | POST | 经济快照上传（Vault/Essentials 采集） | P5 |
| `/internal/v1/commands/whitelist` | GET | whitelist 指令队列（bukkit 模式） | P5 |
| `/internal/v1/activity` | POST | 会话时长上报（v1.4，在线时长任务/成就数据源） | ✅ P5 |
| `/internal/v1/signin` | POST | 游戏内每日签到（v1.4） | ✅ P5 |
| `/internal/v1/mail/pending`、`/internal/v1/mail/claimed` | GET/POST | 奖励邮件（v1.4） | ✅ P5 |
| `/internal/v1/title/active|mine`、`/internal/v1/title/equip` | GET/POST | 称号（v1.4，active 供 PAPI） | ✅ P5 |
| `/internal/v1/kit/save`、`/internal/v1/kit/list` | POST/GET | 礼包采集（v1.4） | ✅ P5 |
| `/internal/v1/admin-ops/{action}`、`/internal/v1/admin-ops/{list,info/{username}}` | POST/GET | `/xmw` 管理通道（审核/封禁/删除） | ✅ P5 |

## 5. 机器人端点 🤖（X-API-Token + astrbot.enabled 门禁，v1.1）

> v1.1 起契约重定义（docs/ASTRBOT_PLAN.md §5.4）：免验证直绑 `/api/astrbot/bind` 已移除；
> `astrbot.enabled=false` 时全部 403。自研对接插件见 `astrbot-plugin/`。

| 端点 | 方法 | 功能 |
|------|------|------|
| `/api/astrbot/status` | GET | `{online, max, servers, version}` |
| `/api/astrbot/players` | GET | `{count, players:[{name, server}], servers:[…]}` |
| `/api/astrbot/bind/request` | POST | QQ 申请绑定验证码 `{qq}` → `{code, expires_in}`（1 次/分钟、5 次/天） |
| `/api/astrbot/unbind` | POST | 按 QQ 解绑 `{qq}` |
| `/api/astrbot/lookup/qq/{qq}` | GET | QQ→账号 `{found, username, status}` |
| `/api/astrbot/lookup/mc/{mc}` | GET | 账号→QQ `{found, qq, bound}` |
| `/api/astrbot/chat` | POST | 群消息上行 `{group, sender_id, sender_name, message}`（群白名单/all/prefix 校验） |
| `/api/astrbot/stream` | GET (SSE) | 下行事件流 `{seq, group, text}`（X-API-Token 请求头，X-Accel-Buffering: no） |
| `/api/astrbot/messages?since=` | GET | 下行轮询 fallback `{messages, latest}` |
| `/internal/v1/qq/bind` | POST | QQ 绑定游戏内确认 `{player, code}`（X-Server-Token） |
| `/internal/v1/messages/pending?since=` | GET | 游戏收件箱 `{messages:[{seq, text}], latest}`（X-Server-Token，插件轮询） |

## 6. 实时通道

| 通道 | 说明 | 落位 |
|------|------|------|
| `ws://host:18899` | 管理后台推送（auth/ping/chat + user_approved/rejected/banned/unbanned 事件） | P4 |
| `GET /api/chat/stream` | 聊天室 SSE（上限 50 连接） | P4 |
| `GET /api/questionnaire/stream` | 问卷评分进度 SSE | P3 |

## 7. 通用约定

- 基址 `http://host:18898`；除静态资源外均在 `/api`、`/internal` 前缀下
- 响应包装：`{"success": bool, "message": str, "data"?: ...}`（错误时附 `msg` 兼容旧前端）
- 限流响应：HTTP 429 + failure 包装
- 时间戳：BIGINT 毫秒 epoch
- 用户状态枚举：`pending / pending_review / pending_verify / invited_pending / approved / rejected / banned`

## 8. v1.2 新增端点 🆕

### 8.1 封禁与公告

| 端点 | 鉴权 | 说明 |
|------|------|------|
| `GET /api/bans` | 公开 | 封禁公示列表(username/avatar/reason/banTime/banUntil) |
| `POST /api/admin/user/ban`(UsernameBody) | 管理员 | days 可选:临时封禁,到期每小时自动解封 |
| `GET /api/announcements` | 公开 | `{news, changelog}`,仅 status=published 且到发布时间的条目 |

### 8.2 照片墙留言与通知

| 端点 | 鉴权 | 说明 |
|------|------|------|
| `GET /api/portal/comments/{photoKey}` / `counts` | 公开 | 留言列表(仅 approved)/计数 |
| `POST /api/portal/comments/{photoKey}` | JWT | 发留言(限频 5/分·用户+IP,敏感词过滤;moderation 开启时 approved=false) |
| `DELETE /api/portal/comments/{id}` | JWT | 删除自己的留言 |
| `GET/POST/DELETE /api/admin/portal/comments/**` | 管理员 | 待审列表/通过/删除 |
| `GET /api/notifications`、`POST read|read-all`、`DELETE /api/notifications/{id}` | JWT | 站内通知(铃铛) |

### 8.3 其他

- `GET /api/admin/export/questionnaires?format=csv|json`:问卷导出
- `GET /api/review/status`:响应补 regTime/questionnaireScoredAt/verifiedAt 时间戳(时间线)

## 9. v1.5 新增端点 🆕（监控与多服 · 社区 · 2FA）

### 9.1 监控与多服

| 端点 | 鉴权 | 说明 |
|------|------|------|
| `GET /api/server/metrics?serverId=&hours=24\|168` | 公开 | 资源指标历史 `{list:[{time,tps1m,tps5m,tps15m,avgTickMs,memUsedMb,memMaxMb,cpuLoad}]}`(dp_server_metrics,7 天) |
| `GET /api/server/status` | 公开 | `tps` 字段改为主服真实 TPS(此前硬编码 20.0,无指标为 null);分服附 tps1m/memUsedMb/memMaxMb/cpuLoad |
| `GET /api/map/live?i={下标}` | 公开 | 后端代理 BlueMap/Dynmap 玩家位置 `{type,mapId,players:[{name,world,x,y,z}]}`;URL 只取 portal.config 已配置条目(防 SSRF) |
| `GET /api/admin/servers` | 管理员 | 服务器注册表 `{mode, servers:[{serverId,name,role,onlinePlayers,lastHeartbeat,live,enabled,hasToken}]}` |
| `POST /api/admin/servers/{id}/issue-token` | 管理员 | 签发/轮换按服令牌(明文仅本次返回,库中存 SHA-256) |
| `PUT /api/admin/servers/{id}/enabled` | 管理员 | 启停该服 internal 通道(per_server 模式生效) |
| `GET/PUT /api/admin/servers/token-mode` | 管理员 | `security.config.tokenMode`: shared(默认) \| per_server |
| HeartbeatRequest 协议 | — | 心跳新增可空字段 `tps1m/tps5m/tps15m/avgTickMs/memUsedMb/memMaxMb/cpuLoad/uptimeSeconds`(旧插件兼容) |

### 9.2 论坛（游客可读，发帖/回帖/点赞 JWT）

| 端点 | 鉴权 | 说明 |
|------|------|------|
| `GET /api/forum/sections`、`GET /threads?sectionId=&page=`、`GET /thread/{id}?page=` | 公开 | 帖子/回复列表详情;游客仅见 published,作者可见自己 pending |
| `POST /api/forum/thread`、`POST /thread/{id}/reply`、`POST /thread/{id}/edit`、`POST /like/{thread\|reply}/{id}` | JWT | 发帖 3/天·回帖 10/天·30s 限频;编辑限发布 10 分钟内(留痕);@提及解析通知 |
| `GET/PUT /api/forum/admin/config` | 管理员 | `{moderation(先审后发,默认 false), likeEnabled(默认 true)}` |
| `GET /api/forum/admin/pending`、`POST /admin/thread/{id}/{action}`、`POST /admin/reply/{id}/{action}` | 管理员 | 审核(approve/reject/delete)+置顶/精华/锁定;动作审计+通知+WS `forum_moderate` |
| `POST /api/forum/admin/section`、`DELETE /admin/section/{id}` | 管理员 | 板块 CRUD(板块下有帖不可删) |

### 9.3 投票与反馈工单

| 端点 | 鉴权 | 说明 |
|------|------|------|
| `GET /api/polls`、`POST /{id}/vote` | 公开/JWT | 列表含我的选择与结果(结果可见性逐场可配 result_visibility: open=实时 / ended=投完或结束后);投票先删后插(uk: poll_id+username+option_id) |
| `POST /api/polls/admin`、`POST /{id}/admin/{open\|close\|delete}`、`POST /{id}/admin/update`、`GET /admin/list` | 管理员 | 生命周期管理;open 全员铃铛;update 改选项清空选票 |
| `POST /api/feedback`、`GET /mine`、`GET /{id}`、`POST /{id}/reply`、`POST /{id}/close` | JWT | 多轮对话工单(3 条/天);关闭后不可追问 |
| `GET /api/feedback/admin/list?status=`、`POST /{id}/admin/reply`、`POST /{id}/close` | 管理员 | 回复=铃铛+邮件(feedback_reply 模板) |

### 9.4 2FA（TOTP + 恢复码 + 邮箱备用）

| 端点 | 鉴权 | 说明 |
|------|------|------|
| `POST /api/login` / `/admin/login` | 公开 | 密码通过且已启用 2FA → `{status:"needs_2fa", challengeId}`;管理员强制开关未绑定 → `{status:"needs_2fa_setup"}`(challenge 为服务端 5 分钟内存凭据,非 JWT,无会话权限) |
| `POST /api/login/2fa {challengeId, code}` | challenge | 验证码依次尝试 TOTP→恢复码→邮箱码;连错 5 次锁 15 分钟;成功签发正式 JWT |
| `POST /api/login/2fa/email {challengeId}` | challenge | 邮箱备用码(内存态 5 分钟,3 次/5 分钟) |
| `POST /api/login/2fa/setup` / `login/2fa/enable` | challenge | 仅 needs_2fa_setup 状态:强制绑定(返回 otpauth secret/确认并返回恢复码+正式 token) |
| `GET /api/user/2fa/status`、`POST setup\|enable\|disable` | JWT | 自助绑定/启用(返回一次性恢复码)/停用(需密码+验证码) |

### 9.5 Webhook 与安全配置

| 端点 | 鉴权 | 说明 |
|------|------|------|
| `GET/PUT /api/admin/webhook/config`、`POST /admin/webhook/test` | 管理员 | `{enabled, urls:[{url,secret}], events[]}`;POST JSON 带 `X-DP-Signature: sha256=HMAC(secret, timestamp.body)` + `X-DP-Timestamp`;事件源挂审计骨架(review.*/feedback.*/forum.*/poll.*/questionnaire.submitted/reward.sent/community.*/server.*),异步+2 次退避重试 |
| `GET/PUT /api/admin/security/config` | 管理员 | `{admin2faRequired, tokenMode}`;强制开关默认关 |
| WS :18899 事件扩展 | — | 新增 `forum_moderate` / `feedback_new` 管理端推送 |

## 10. v1.6 新增端点 🆕（主页装修 · 邮件群发）

| 端点 | 鉴权 | 说明 |
|------|------|------|
| `GET /api/user/profile-custom` | JWT 本人 | 读取本人主页自定义（含 css） |
| `PUT /api/user/profile-custom` | JWT 本人 | 保存主页自定义；css 服务端 CssSanitizer 消毒（剔除 @import/javascript:/position:fixed，选择器加 `#pc-root` 前缀，≤8000 字符），banner/bgImage 支持上传地址或 `/uploads/*` |
| `GET /api/admin/mail/targets` | 管理员 | 群发目标数 + SMTP 状态 `{count, configured}`（configured=false 时投递走日志模式） |
| `POST /api/admin/mail/broadcast` | 管理员 | `{subject≤200, content≤20000}`；目标=dp_user 邮箱非空合法小写去重，异步逐目标发送，审计码 `mail_broadcast` |
| 飞书卡片展示 | — | WebhookDispatcherService 对飞书 hook 自动转 interactive 卡片：事件中文名标题 + 字段中文标签（操作人/对象/详情）+ 北京时间，`event/time/action` 元字段隐藏 |
