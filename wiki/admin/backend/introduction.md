# 后端 · 架构与技术栈

## 定位

DreamPort 后端是整套系统的**唯一业务中枢**:门户网站(前端 SPA 由后端静态托管)、REST API、WebSocket 推送、管理后台、邮件、定时任务全在这里。各插件(Arch/Velocity/AstrBot/皮肤站)都是它的薄客户端。

## 技术栈

| 项 | 说明 |
|---|---|
| 框架 | Spring Boot 3.4(Java 21,**虚拟线程**全开) |
| 数据访问 | Spring Data JDBC + JdbcTemplate(复杂查询) |
| 数据库 | **仅支持 MySQL**,Schema 由 Flyway 管理(嵌入迁移 `db/migration/mysql`) |
| 认证 | 无状态 JWT(io.jsonwebtoken,HS384) |
| 邮件 | Jakarta Mail;未配置 SMTP 时自动进入**日志模式**(内容打印到日志) |
| 前端 | Vue 3 + Vite 构建产物嵌入 `classpath:/static`,SPA 路由由后端转发 |

## 多模块结构

```
DreamPort/
├── dreamport-common/        # 插件↔后端共享协议(端点常量、DTO)
├── dreamport-server/        # 后端(本文档主角)
├── dreamport-plugin/        # Paper 主插件
├── dreamport-plugin-proxy/  # Velocity 代理插件
├── frontend/                # Vue 3 前端源码(构建产物进后端 JAR)
├── astrbot-plugin/          # AstrBot QQ 插件(Python)
└── bs-plugin-dreamport/     # BlessingSkin 皮肤站插件(PHP)
```

后端主要包(`cn.xmcraft.dreamport.server`):

| 包 | 职责 |
|---|---|
| `api` | 全部 REST 控制器(22 个,约 190+ 端点) |
| `security` | JWT 签发/校验、AuthFilter、密码哈希(bcrypt,兼容旧版 SHA 透明升级) |
| `user` / `review` / `invite` | 用户、审核状态机、邀请码 |
| `questionnaire` | 问卷与 AI 评分(OpenAI 兼容接口) |
| `verification` | 邮箱验证码、图形验证码、ID 验证(3 分钟进服记录窗口) |
| `chat` | 聊天消息(落库,7 天+5 万条上限) |
| `qq` | QQ 绑定验证码、群服消息桥(双向队列) |
| `blessingskin` | 皮肤站账号开通与同步 |
| `stats` | 服务器心跳、在线历史(7 天曲线) |
| `economy` | 经济快照汇总 |
| `notification` | 站内通知(铃铛) |
| `infra` | 邮件、审计日志、上传文件 |
| `settings` | dp_setting 业务配置(管理后台热生效) |
| `migration` | 旧版 XMWhitelist 数据迁移(同库/SQL dump/文件三种) |

## 鉴权体系(四套,互不通用)

| 体系 | 使用者 | 请求头 | 签发处 |
|---|---|---|---|
| 用户 JWT | 网页/前端 API | `Authorization: Bearer` | 登录/注册时签发,TTL 默认 7 天 |
| 服务器令牌 | Paper/Velocity 插件 | `X-Server-Id` + `X-Server-Token` | config.yml `wl.internal.server-token` |
| AstrBot 令牌 | AstrBot 插件 | `X-API-Token` | 管理后台生成,存 dp_setting |
| 皮肤站共享密钥 | 皮肤站插件 | `X-Dreamport-Secret` | 管理后台 BlessingSkin 互通 |

另有公开 OAuth2 Provider 端点(`/oauth2/token` 等)用 client_id/secret 参数鉴权,供皮肤站换取用户 JWT。

## 统一响应包装

除 OAuth2 标准端点外,所有 API 返回:

```json
{ "success": true, "message": "…", "data": { … } }
```

失败时 `success=false` 且带 `message`/`msg`(双字段兼容旧前端)。

## 端口

| 端口 | 用途 | 配置 |
|---|---|---|
| 18898 | HTTP(REST+前端) | `server.port` / config.yml |
| 18899 | WebSocket 推送 | `wl.ws-port` / config.yml |
