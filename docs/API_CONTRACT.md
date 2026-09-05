# DreamPort API 契约（P0 冻结稿）

> 本表从旧版 `ApiRouter.java`（80+ context）与 `api.ts`（80+ 方法）逐字冻结，是 `/api/**` 契约的权威清单。
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
| `/api/chat/history`、`/api/chat/save` | GET/POST | 聊天室历史 | P4 |
| `/api/auth/forgot-password` ⚡ | POST | 忘记密码（防枚举） | P3 |
| `/api/auth/reset-password` | POST | 重置密码（令牌 1h） | P3 |
| `/api/auth/validate` 🔒 | GET/POST | Token 校验 | ✅ P1 |
| `/api/cmi/stats[/extended]`、`/api/cmi/wealth`、`/api/cmi/playtime`、`/api/cmi/activedays`、`/api/cmi/online`、`/api/cmi/banned`、`/api/cmi/player/:name` | GET | 经济/时长/在线统计（**FIX(legacy)**：真实统计，移除估算值） | P4 |
| `/api/village/list` | GET | 村民族谱（仅 approved） | P4 |
| `/api/machine/list` | GET | 公共机器（仅 approved） | P4 |
| `/api/players/list`、`/api/players/profile/:name` | GET | 玩家目录/档案 | P4 |
| `/api/microsoft/verify/start` 🔒、`/api/microsoft/callback` | GET | Microsoft 正版验证链 | P3 |

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

## 3. 管理端点 👑

| 端点 | 方法 | 功能 | 落位 |
|------|------|------|------|
| `/api/admin/login` ⚡ | POST | 管理员登录（OP 名单 + 密码） | ✅ P1→P4 |
| `/api/admin/verify` | POST | 管理 token 校验（吸收 参考项目） | P4 |
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
| `/api/admin/sync` | POST | bukkit 白名单同步 | P5 |
| `/api/admin/export/{users,audits}` | GET | 导出（CSV/JSON） | P7 |
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

## 5. 机器人端点 🤖（X-API-Token）

| 端点 | 方法 | 功能 | 落位 |
|------|------|------|------|
| `/api/astrbot/status` | GET | 服务器/白名单状态 | P7 |
| `/api/astrbot/players` | GET | 在线玩家 | P7 |
| `/api/astrbot/{bind,unbind}` | POST | QQ↔MC 绑定/解绑 | P7 |
| `/api/astrbot/lookup/{qq,mc}/:id` | GET | 双向查询 | P7 |
| `/api/astrbot/chat` | POST | 消息进服广播 | P7 |

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
