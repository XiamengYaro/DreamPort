# DreamPort 完整项目文档

> **DreamPort · 夏日小镇 · 梦港** —— Minecraft 服务器门户与玩家管理系统
> 版本 `1.0.0` ｜ 协议 MIT ｜ 仓库 `http://10.0.0.6:11488/Xiameng/DreamPort.git`
> 实时进度：[IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md)

---

## 1. 项目概览

### 1.1 定位

DreamPort 是旧版 **XMWhitelist**（已归档于 `../XMWhitelist-Legacy/`）的全量重写，为 Minecraft 服务器（主用例：夏日小镇 ★XMCraft）提供**门户网站 + 玩家/白名单管理**的一体化平台：

- 玩家侧：官网门户、注册（邮箱验证码 + 图形验证码）、入服问卷（AI 评分）、ID 验证、邀请码、个人中心、排行榜、村民族谱、公共机器、网页聊天
- 管理侧：审核工作台（实时推送）、问卷题库管理、门户/文档/下载中心管理、统计与审计、维护模式、多服管理
- 机器人侧：QQ（AstrBot）绑定查询与消息桥

### 1.2 品牌与命名

| 项 | 值 |
|----|----|
| 英文名 | DreamPort |
| 中文名 | 夏日小镇 · 梦港（主品牌 夏日小镇，项目名 梦港，作者署名 夏梦/XiaMeng/XM） |
| Java 包根 | `cn.xmcraft.dreamport` |
| Maven groupId | `cn.xmcraft` |
| 新数据库表前缀 | `dp_` |
| 插件名 | `DreamPort` |
| 端口 | `18898`（HTTP/REST/SPA）、`18899`（WebSocket）—— 与旧版一致 |

### 1.3 谱系声明

DreamPort 为独立架构重写：Java 代码全部原创（旧版仅作行为规范参考，禁止复制）；前端整体继承自研代码；对同类方案的吸收仅限功能思想。

---

## 2. 系统架构

### 2.1 拓扑

```
┌───────────────────────────┐           ┌────────────────────────────────────┐
│  Minecraft 服务器集群       │           │ dreamport-server（独立后端进程）      │
│                           │           │ Spring Boot 3.4 · Java 21 虚拟线程   │
│ ┌───────────────────────┐ │  HTTP/JSON│ ┌────────────────────────────────┐ │
│ │ dreamport-plugin      │◀┼──────────▶│ │ REST API /api/**   :18898      │ │
│ │ primary（主服）        │ │ /internal │ │ SPA 静态托管        :18898      │ │
│ │ · 进服校验(缓存+兜底)  │ │  +token   │ │ WebSocket          :18899      │ │
│ │ · 事件上报/经济快照    │ │           │ │ Flyway + HikariCP → MySQL       │ │
│ └───────────────────────┘ │           │ │ 迁移器 / LLM / SMTP / OAuth     │ │
│ ┌───────────────────────┐ │           │ └────────────────────────────────┘ │
│ │ secondary（子服）      │◀┼───心跳────▶│                                    │
│ │ · 状态/玩家列表上报    │ │           └────────────────────────────────────┘
│ └───────────────────────┘ │                        ▲
│ ┌───────────────────────┐ │                        │ 浏览器
│ │ proxy（Velocity/BC）  │◀┼──登录拦截──▶   Vue 3 SPA（升级自旧版前端）
│ │ · 代理端统一校验       │ │
│ └───────────────────────┘ │
└───────────────────────────┘
```

### 2.2 三种插件运行形态（同一 JAR，配置区分）

| 角色 | 安装位置 | 职责 |
|------|----------|------|
| `primary` | Paper/Folia 主服 | 进服白名单校验（本地缓存 + fail_policy 兜底）、登录记录、聊天/进出事件、经济快照采集、whitelist 指令执行、`/xmw` 管理命令 |
| `secondary` | 各子服 | 心跳状态上报（在线数/玩家列表）、事件上报——**合并替代旧版 XMWhitelist-Bridge** |
| `proxy` | Velocity / BungeeCord | 代理端统一登录拦截，群组服一处校验全组生效 |

### 2.3 技术栈

| 层 | 选型 | 说明 |
|----|------|------|
| 后端 | Java 21 + Spring Boot 3.4 | `spring.threads.virtual.enabled=true`，IO 密集全虚拟线程 |
| 数据访问 | Spring Data JDBC + Flyway + HikariCP | 版本化迁移、连接池化 |
| 数据库 | MySQL 8（utf8mb4）；dev profile 用 H2(MySQL 模式) | 免依赖快速启动 |
| 鉴权 | JJWT（HS256，role claim，7 天）+ bcrypt(+legacy SHA 兼容) | 见第 5 节 |
| 实时 | WebSocket（审核推送）+ SSE（问卷评分/聊天） | 端口 18899 与 /api 路径同旧版 |
| 外部 | DeepSeek/OpenAI 兼容 LLM、SMTP(jakarta.mail)、Microsoft OAuth、Vault/Essentials、AstrBot | |
| 插件 | Paper API 1.20（api-version 1.20，folia-supported）、java.net.http + Caffeine | |
| 前端 | Vue 3 + Vite + Pinia + vue-i18n + TailwindCSS + marked/DOMPurify | 继承旧版 18 页面 |

---

## 3. 模块设计

```
dreamport-common   cn.xmcraft.dreamport.common
└── protocol/      LoginCheckRequest/Response、事件与快照 DTO、ErrorCode、Protocol 常量

dreamport-server   cn.xmcraft.dreamport.server
├── api/           对外 /api/**（契约与旧版逐字兼容）+ /internal/v1/**（服务器端点）
├── domain/        account / verification / questionnaire / review / invite / notification /
│                  appeal / village / machine / player / economy / chat / docs / portal /
│                  downloads / stats / bridge / astrbot
├── security/      TokenService(JWT) / PasswordService(双算法) / 三通道鉴权过滤 / 限流
├── storage/       仓储接口 + MySQL 实现 + dp_setting 键值配置服务
├── migration/     LegacyMigrator（旧库/JSON/config 导入）+ Flyway 脚本
├── infra/         LlmClient(熔断) / MailService(模板) / 调度 / 时序数据
└── ws/            审核实时推送

dreamport-plugin   cn.xmcraft.dreamport.plugin
├── listener/      登录校验 / 聊天 / 进出
├── internal/      BackendClient(HttpClient+缓存) / EconomyCollector / WhitelistQueue
├── command/       /xmw（含 delete）
└── config/        role/后端地址/凭据/fail_policy

frontend/          Vue 3 SPA（18 页面，见进度表 P6）
```

---

## 4. 数据架构

### 4.1 新 Schema（Flyway 管理，前缀 `dp_`，utf8mb4，主键 `id BIGINT AUTO_INCREMENT`）

| 表 | 用途 | 对应旧表 |
|----|------|----------|
| `dp_user` | 用户/白名单主体（30+ 列：账户状态、问卷、MC/基岩绑定、封禁、qq_number、头像） | `xmwhitelist_users` |
| `dp_audit_log` | 审计日志 | `xmwhitelist_audits` |
| `dp_invite` | 邀请码 | `xmwhitelist_invites` |
| `dp_notification` | 站内通知 | `xmwhitelist_notifications` |
| `dp_pending_login` | 进服登录尝试（ID 验证比对，3 分钟窗口） | `xmwhitelist_pending_logins` |
| `dp_password_reset` | 密码重置令牌（1 小时） | `xmwhitelist_password_resets` |
| `dp_appeal` | 问卷申诉 | `xmwhitelist_appeals` |
| `dp_village_trade` | 村民族谱 | `village_trades` |
| `dp_public_machine` | 公共机器 | `public_machines` |
| `dp_setting` | 站点设置键值（门户/背景/公告/维护模式/验证页/下载） | 旧版写 config.yml 的部分 |
| `dp_server` | 注册服务器实例（id/角色/token 哈希/心跳） | 旧版 Bridge 内存态 |
| `dp_questionnaire` / `dp_question` / `dp_question_option` | 问卷题库（带 YAML 导入导出） | 旧版 questionnaire.yml |

**时间戳一律 BIGINT 毫秒 epoch**（与旧版一致，保证前端零适配）。

### 4.2 用户状态机（与旧版兼容）

```
pending ──答题──▶ pending_review ──▶ approved ──▶ banned(可解禁回 approved)
   │                  ▲                                  │
   │                  └── 申诉通过/邀请确认 ─────────────┘
   ├──▶ invited_pending（邀请码注册，等邀请人确认）
   └──▶ pending_verify（绑定 MC ID 后，进服完成验证）
rejected（可申诉→pending_review；可重答题）
```

### 4.3 密码双算法（兼容核心）

| 算法 | 格式 | 行为 |
|------|------|------|
| `bcrypt`（新默认） | `$2a$...` cost 10 | 新注册/改密/升级后写入 |
| `legacy_sha256`（迁移保留） | `$SHA$<base64(16字节盐)>$<sha256_hex(盐+密码)>` | 恒时比较验证；**验证成功即透明升级为 bcrypt** |

### 4.4 迁移器（首次启动自动执行）

1. 检测旧库存在 `xmwhitelist_users` 等表 → 逐表 `RENAME TABLE … TO legacy_<表>_backup`（原地备份）
2. 按列映射导入新 `dp_*` 表（id 原值保留；密码哈希原样入 `password_hash` + `password_algo='legacy_sha256'`；qq 字段补录）
3. `users.json` / `audits.json`（file 模式）若存在同样导入
4. 旧 `config.yml` → SMTP/MySQL 等基础设施引导 + 门户/背景/公告/验证页 → `dp_setting`
5. 输出迁移报告（各表行数/跳过行）；幂等可重跑；全程 Testcontainers 测试覆盖

---

## 5. 安全设计

| 通道 | 凭据 | 覆盖 |
|------|------|------|
| 用户/管理员 | `Authorization: Bearer <JWT>`（claim: username, role） | `/api/user/**`、`/api/admin/**` |
| 服务器→后端 | `X-Server-Id` + `X-Server-Token`（dp_server 表哈希存储） | `/internal/v1/**` |
| 机器人 | `X-API-Token` | `/api/astrbot/*` |

- 限流（IP 维度可配）：登录 5/min、注册 3/min、验证码 3/5min
- 密码策略：`register.username_regex`、密码长度/正则可配
- 评分安全：LLM 结果带 `confidence`，低于阈值自动 `manualReview` 进管理后台复核队列
- 凭据管理：`application-local.yml`（gitignore）或环境变量；仓库内严禁真实密钥
- 插件兜底：后端不可达时按 `fail_policy`（cache/allow/deny）+ 本地缓存（TTL 60s）决策，防锁服

---

## 6. API 契约

原则：**对外 `/api/**` 与旧版逐字兼容**（前端 `api.ts` 约 80 个方法最小改动）；有意修复的偏差在代码标 `// FIX(legacy)` 并记 CHANGELOG。分组概览（完整冻结表见 P0-7 产出）：

| 分组 | 代表端点 |
|------|----------|
| 公开 | `/api/config`、`/api/captcha/generate`、`/api/verify/send`、`/api/register`、`/api/login`、`/api/review/status`、`/api/questionnaire/{config,submit,stream}`、`/api/version`、`/api/server/status`、`/api/docs`、`/api/downloads`、`/api/cmii`（经济榜单）、`/api/village/list`、`/api/machine/list`、`/api/players/*`、`/api/auth/{forgot-password,reset-password,validate}` |
| 用户 🔒 | `/api/user/{status,profile,password,email/update,avatar/upload}`、`/api/user/{minecraft,bedrock}/*`、`/api/verify/{check,status}`、`/api/invite/*`、`/api/notifications*`、`/api/questionnaire/appeal`、`/api/village/submit`、`/api/machine/{submit,upload}`、`/api/chat/{send,stream}` |
| 管理员 👑 | `/api/admin/login`、`/api/admin/users`、`/api/admin/user/*`（含 batch-*）、`/api/admin/questionnaire/*`、`/api/admin/appeals*`、`/api/admin/docs/*`、`/api/admin/{portal,background,server-config,system-config,upload,maintenance,sync,audits,stats/*,export/*}` |
| 服务器 | `/internal/v1/{login-check,login-record,events/*,economy/snapshot,commands/whitelist,heartbeat}` |
| 机器人 | `/api/astrbot/{status,players,bind,unbind,chat,lookup/{qq,mc}/*}` |
| 实时 | `ws://:18899`（审核推送）、`GET /api/chat/stream`（SSE）、`GET /api/questionnaire/stream`（SSE） |

---

## 7. 配置参考

### 7.1 后端 application.yml（基础设施）

```yaml
server.port: 18898          # REST + SPA
wl.ws-port: 18899           # WebSocket
spring.profiles: dev(H2) / mysql
spring.datasource.*         # MySQL 连接
wl.security.jwt-secret      # 生产必须由环境变量注入
wl.internal.server-token    # 服务器间凭据引导值
wl.llm.*                    # provider/api_base/api_key/model/熔断参数
wl.mail.*                   # SMTP
wl.migration.legacy-*       # 旧库/旧文件路径（迁移器用）
```

### 7.2 站点设置（dp_setting，管理后台可视化编辑）

门户（server_name/logo/carousel/team/features/timeline/icp/social）、背景与公告、验证页文案、下载中心、维护模式、管理员通知邮箱（**修复旧版硬编码**）。

### 7.3 插件 config.yml

```yaml
role: primary            # primary | secondary | proxy
backend.url: http://127.0.0.1:18898
backend.server-id / server-token
check.fail-policy: cache # cache | allow | deny
check.cache-ttl-seconds: 60
features.enforce-whitelist / forward-chat / report-join-quit: true
```

---

------|------|
| XMWhitelist-Legacy | 功能 100% 对标基线；数据迁移来源；行为规范唯一参考 |

------|------|
| XMWhitelist-Legacy | 功能 100% 对标基线；数据迁移来源；行为规范唯一参考 |
| 参考项目 v1.8.0 | 仅吸收功能思想（见下），**不复制代码**（GPL-3.0） |

**参考项目 吸收清单**（除 Discord 经评估排除外全部纳入）：

✅ 评分 confidence+manualReview 人工复核 ｜ ✅ 游戏内 delete 命令 ｜ ✅ i18n 全覆盖 ｜ ✅ 注册阶段化校验+requestId 日志 ｜ ✅ 代理端统一拦截（proxy 角色）｜ ✅ bStats 匿名统计（插件侧）｜ ✅ 前端语言切换/ui 组件库/组件测试 ｜ ✅ config_help 双语自说明 ｜ ✅ CI 构建与 Release 工作流（Gitea Actions）｜ ➖ Discord OAuth（排除）

---

## 9. 构建与部署

```bash
# 构建
mvn clean package                                   # 后端 + 插件
cd frontend && npm ci && npm run build              # 前端 → dreamport-server 托管

# 运行（首次启动自动执行旧库迁移）
java -jar dreamport-server/target/dreamport-server-0.1.0.jar --spring.profiles.active=mysql

# 插件
cp dreamport-plugin/target/dreamport-plugin-0.1.0.jar <服务器>/plugins/
```

交付物规划（P8）：systemd unit、Dockerfile、插件 jar、迁移演练报告、压测报告。

## 10. 文档索引

| 文档 | 内容 |
|------|------|
| [README.md](../README.md) | 项目简介、架构图、SemVer 规范、路线图 |
| [USER_GUIDE.md](USER_GUIDE.md) | **使用文档**（部署/配置/插件/迁移/玩家与管理员指南/FAQ） |
| [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md) | **128 项功能实现进度总表（实时更新）** |
| [AGENTS.md](../AGENTS.md) | AI 协作操作指南 |
| [Rules.md](../Rules.md) | 工程硬规则 |
| [CHANGELOG.md](../CHANGELOG.md) | 变更日志 |
