# 后端 · API 参考

> 完整逐字段契约见仓库 `docs/API_CONTRACT.md`;本文按组给出端点地图。统一响应包装 `{success, message, data}`;除注明外鉴权方式为 `Authorization: Bearer <JWT>`。

## 鉴权四体系回顾

| 体系 | 头 | 适用 |
|---|---|---|
| 用户 JWT | `Authorization: Bearer` | `/api/**` 大多数端点 |
| 服务器令牌 | `X-Server-Id` + `X-Server-Token` | `/internal/v1/**`(插件) |
| AstrBot 令牌 | `X-API-Token` | `/api/astrbot/**` |
| 皮肤站共享密钥 | `X-Dreamport-Secret` | 皮肤站插件→后端的内部调用 |

## 公开端点(无鉴权)

| 端点 | 说明 |
|---|---|
| `GET /api/health` | 健康检查(version/virtualThread) |
| `POST /api/register` / `POST /api/login` | 注册/登录(邮箱验证码、图形验证码、邀请码、限频) |
| `POST /api/admin/login` | 管理员登录(对齐旧版语义) |
| `POST /api/auth/forgot-password` / `reset-password` | 找回密码 |
| `GET /api/config` | 门户公开配置(公告/背景/门户内容) |
| `GET /api/announcements` | 公告页(资讯+更新日志,仅已发布) |
| `GET /api/docs` / `GET /api/docs/{slug}` | 官网文档库 |
| `GET /api/review/status?username=` | 免登录查询申请进度(时间线) |
| `GET /api/bans` | 公开封禁列表页数据 |
| `GET /api/server/status` / `player-history?days=` | 服务器在线状态/7 天在线曲线 |
| `GET /api/portal/comments/{key}` / `counts` | 照片墙留言(仅已审核) |
| `GET /api/leaderboard` 等 Community 公开端点 | 排行榜/村谱/机器展示 |

## 用户端点(JWT)

| 组 | 端点 | 说明 |
|---|---|---|
| 资料 | `GET/POST /api/user/profile`、`POST /api/user/avatar/upload` | 资料、头像上传(≤2MB) |
| 密码/邮箱 | `POST /api/user/password`、`POST /api/user/email/update` | 改密(同步皮肤站)、改邮箱(皮肤站先行) |
| ID 验证 | `POST /api/user/minecraft/set|verify`、`GET status`、`POST /api/user/bedrock/set|verify|cancel|status` | Java/基岩验证(3 分钟进服窗口) |
| 问卷 | `/api/questionnaire/**` | 拉题、提交、结果、申诉 |
| QQ 绑定 | `GET /api/user/qq/status`、`POST /api/user/qq/bind|unbind` | 网页侧 QQ 绑定 |
| 皮肤站 | `GET /api/user/bs/players`、`POST /api/user/bs/provision` | 皮肤站角色/一键开通 |
| 通知 | `GET /api/notifications`、`POST read|read-all`、`DELETE /{id}` | 铃铛中心 |
| 聊天 | `GET /api/chat/history`、`POST /api/chat/send` | 网页聊天室(限频 10/分,敏感词过滤) |
| 照片墙 | `POST /api/portal/comments/{key}`、`DELETE /api/portal/comments/{id}` | 留言(限频 5/分)、删自己的 |
| OAuth2 | `GET /api/oauth2/authorize-info`、`POST /api/oauth2/authorize` | 授权确认页(皮肤站 SSO) |
| 微软绑定 | `GET /api/auth/microsoft/start`、`GET /callback` | 正版账号绑定(需配置 Azure) |
| 社区 | `/api/village/**`、`/api/machines/**`、`/api/player/{name}` 等 | 村谱/机器提交与展示、玩家档案(经济/时长合并) |

## 管理端点(JWT + 管理员)

| 组 | 说明 |
|---|---|
| `/api/admin/settings/**` | register/llm/questionnaire/invite/game/astrbot/blessingskin/announcements/downloads(GET/PUT,secret 脱敏回显) |
| `/api/admin/**`(ReviewAdmin) | 审核/封禁(days)/解封/删除/申诉处理/基岩重置 |
| `/api/admin/users/**`(SiteAdmin) | 玩家管理、导出(玩家/审计/问卷 CSV/JSON) |
| `/api/admin/stats/**` | 总览/注册趋势/问卷统计 |
| `/api/admin/audits` | 审计日志查询 |
| `/api/admin/appeals/**` | 申诉列表与处理 |
| `/api/admin/questionnaire/**` | 题库整卷读写 |
| `/api/admin/portal/**` | 照片墙管理、留言审核/删除 |
| `/api/admin/docs/**` | 官网文档 CRUD |
| `/api/admin/migration/**` | 旧库迁移(检测/dump 上传/文件导入) |
| `/api/admin/export/**` | 数据导出 |

## 插件端点(`/internal/v1/**`,服务器令牌)

| 端点 | 方向 | 说明 |
|---|---|---|
| `POST login-check` | 插件→后端 | 进服校验(decision/reasonKey/maintenance;结果后端侧短缓存) |
| `POST login-record` | 插件→后端 | 进服记录(ID 验证 3 分钟窗口依据) |
| `POST events` | 插件→后端 | join/quit/chat 事件(含 serverId,驱动 QQ 桥/聊天落库) |
| `POST heartbeat` | 插件→后端 | 心跳(serverId/role/在线列表),驱动 dp_server 落库与离线告警 |
| `POST economy/snapshot` | 插件→后端 | 经济快照(主服上报) |
| `GET commands/whitelist?serverId=` | 插件→后端 | 管理后台下发的白名单指令队列 |
| `GET messages/pending?since=` | 插件→后端 | 游戏收件箱(网页/QQ→游戏广播) |
| `POST qq/bind` | 插件→后端 | 游戏内 QQ 绑定确认 |
| `GET/POST admin-ops/**` | 插件→后端 | 游戏内管理命令(/xmw)转发 |

## AstrBot 端点(`/api/astrbot/**`,X-API-Token)

`POST bind/request`(6 位验证码,5 分钟,限频 1/分 5/天)、`POST unbind`、`GET lookup/qq/{qq}`、`GET status`、`GET players`、`POST chat`(群消息上行)、`GET stream`(SSE 下行长连)、`GET messages`(轮询兜底)。集成关闭时全部 403。

## OAuth2 Provider 端点(皮肤站 SSO)

| 端点 | 鉴权 | 说明 |
|---|---|---|
| `GET /api/oauth2/authorize-info` | JWT | 授权页校验 client/redirect |
| `POST /api/oauth2/authorize` | JWT | 签发授权码(5 分钟,单次),返回 redirectUrl |
| `POST /oauth2/token` | client 凭据 | code 换 access_token(=站内 JWT) |
| `GET /oauth2/userinfo` | Bearer | username/email/nickname/minecraftName |
| `GET /api/user/bs/players`、`POST /api/user/bs/provision` | JWT | 皮肤站角色拉取(缓存 5 分钟)/一键开通 |

## 限频速查

登录 5/分·IP、注册 3/分·IP、聊天 10/分·用户、照片留言 5/分·用户+IP、QQ 验证码 1/分·QQ 与 5/天·QQ。
