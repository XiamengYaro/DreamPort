# 后端 · 配置详解

配置分两层,**职责不同**:

| 层 | 文件 | 内容 | 生效方式 |
|---|---|---|---|
| 基础设施 | 工作目录 `config.yml`(首启自动生成) | 数据库/JWT/服务器令牌/SMTP/端口 | 重启生效 |
| 业务设置 | 数据库 `dp_setting` | 注册/问卷/AI/邀请/群绑定/门户内容等 | 管理后台改,**热生效** |

## config.yml 全键

> 完整模板首次启动自动生成,以下为关键键;打包默认值见 `application.yml`。

### 数据库

| 键 | 默认 | 说明 |
|---|---|---|
| `database.url` | `jdbc:mysql://127.0.0.1:3306/dreamport?...` | 仅支持 MySQL |
| `database.username` / `database.password` | — | 需 DDL 权限(Flyway) |

对应环境变量:`WL_DB_HOST` / `WL_DB_PORT` / `WL_DB_NAME` / `WL_DB_USER` / `WL_DB_PASSWORD`。

### 安全

| 键 | 默认 | 说明 |
|---|---|---|
| `security.jwt-secret` | `dev-only-change-me-…` | **生产必须换**(≥32 位随机串);环境变量 `WL_JWT_SECRET` |
| `security.jwt-ttl-days` | `7` | 登录态有效期 |

### 服务器间令牌

| 键 | 默认 | 说明 |
|---|---|---|
| `internal.server-token` | `dev-internal-token` | 与 Paper/Velocity 插件 `backend.server-token` 一致;环境变量 `WL_SERVER_TOKEN` |

### 邮件 SMTP

| 键 | 默认 | 说明 |
|---|---|---|
| `smtp.host` | 空 | **留空 = 日志模式**(邮件内容打印到日志,不发信) |
| `smtp.port` | 465 | |
| `smtp.username` / `smtp.password` | 空 | SMTP 授权码(不是邮箱登录密码) |
| `smtp.from` | `no_reply@xmcraft.cn` | 发件人地址 |
| `smtp.ssl` | true | 465 端口一般 true;587 用 false+STARTTLS 视邮件商 |
| `smtp.subject` | 空 | 主题前缀(可空) |

环境变量:`WL_SMTP_HOST/PORT/USER/PASSWORD/FROM`。

### 其他

| 键 | 默认 | 说明 |
|---|---|---|
| `server.port` | 18898 | HTTP(API+前端) |
| `ws-port` | 18899 | WebSocket |
| `web-register-url` | — | 进服被踢提示中的注册地址 |
| `wl.cors.allowed-origins` | `*` | 跨域白名单 |
| `wl.migration.enabled` | true | 启动时自动检测旧库并迁移(幂等) |
| `wl.migration.legacy-dir` / `legacy-config` | 空 | 旧版 file 模式目录 / 旧 config.yml 路径 |

`spring.servlet.multipart` 上限 128MB(旧库 dump 上传)。

## dp_setting 业务设置(管理后台配置,热生效)

| 管理后台页面 | 主要键 | 内容 |
|---|---|---|
| 系统设置 | `register.config` | 邮箱验证码开关、验证码开关、同邮箱账号上限、邮箱域名白名单、自动过审、用户名正则 |
| 外观设置 | `llm.config` | AI 评分:接口地址/Key/模型/超时/并发/系统提示词 |
| 外观设置 | `questionnaire.config` | 问卷启用、及格分 |
| 外观设置 | `invite.config` | 邀请码启用、有效期、每人上限 |
| 外观设置 | `game.config` | 白名单模式、注册页地址、基岩版开关与前缀 |
| 外观设置 | `downloads.list` | 下载中心条目(JSON) |
| 系统设置 | `admins.list` | 管理员名单 |
| 系统设置 | `maintenance.enabled` | 维护模式 |
| 系统设置 | `mail.admin_notify_email` | 管理员通知邮箱 |
| 系统设置 | `sensitive.words` | 聊天/留言敏感词(替换为 ***) |
| 系统设置 | `photo.comment.moderation` | 照片墙留言先审后发 |
| 系统设置 | `astrbot.enabled` / `astrbot.api_token` | QQ 互通开关与令牌(群绑定另存) |
| 系统设置 | `news.list` / `changelog.list` | 公告页资讯与更新日志 |
| 门户管理 | `portal.config` / `background.config` | 门户内容、轮播图、照片墙、背景 |

## 优先级与覆盖

```
命令行参数(--server.port=xxx) > 环境变量(WL_JWT_SECRET 等) > config.yml > application.yml 打包默认
```

## 业务配置改后要重启吗?

不用。`dp_setting` 全部热生效;只有 `config.yml` 基础设施项需要重启。
