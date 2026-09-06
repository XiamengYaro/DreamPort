# DreamPort 使用文档

> **DreamPort · 夏日小镇 · 梦港** —— Minecraft 服务器门户与玩家管理系统
> 适用版本：v1.0.0+ ｜ 面向读者：服主/运维（§1–5、§7–9）与玩家/管理员（§6）

---

## 目录

- [1. 快速开始（5 分钟本地体验）](#1-快速开始)
- [2. 生产部署](#2-生产部署)
- [3. 后端配置说明](#3-后端配置说明)
- [4. 插件安装与角色](#4-插件安装与角色)
- [5. 旧版数据迁移](#5-旧版数据迁移)
- [6. 功能使用指南（玩家 / 管理员）](#6-功能使用指南)
- [7. 命令与权限](#7-命令与权限)
- [8. 常见问题 FAQ](#8-常见问题-faq)
- [9. 安全清单](#9-安全清单)
- [附：文档索引](#附文档索引)

---

## 1. 快速开始

**只需 MySQL + 一个 JAR**，首次启动自动生成配置文件并进入初始化向导：

```bash
# 0. 准备数据库（一次性）
mysql -uroot -e "CREATE DATABASE dreamport CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 1. 首次启动 —— 自动在工作目录生成 config.yml（数据库需先在 config.yml 填好）
java -jar dreamport-server-1.0.0.jar

# 2. 编辑 config.yml：填 MySQL 连接（[必改]），改 jwt-secret / server-token，重启
nano config.yml && java -jar dreamport-server-1.0.0.jar

# 3. 浏览器打开 http://localhost:18898/setup 进入初始化向导：
#    ① 创建管理员账号  ② 选择「全新部署」或「上传旧库 .sql 导入」
open http://localhost:18898/setup
```

- 后端仅支持 **MySQL**；SMTP 未配置时邮件为**日志模式**（验证码打印在后端日志）
- 默认端口：**18898**（网页+API）、**18899**（WebSocket 实时推送）

---

## 2. 生产部署

### 2.1 准备

| 项 | 要求 |
|----|------|
| Java | 21+ |
| MySQL | 8.x（utf8mb4） |
| 数据库 | `CREATE DATABASE dreamport CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` |
| 端口 | 18898（TCP，对玩家开放）、18899（TCP，管理后台用；可走内网/反代） |
| SMTP | 任一支持 SMTP 的邮箱（不配则验证码功能不可用） |

### 2.2 部署流程（config.yml 单文件 + 初始化向导）

```bash
# ① 建库（一次性）
mysql -uroot -e "CREATE DATABASE dreamport CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# ② 首次启动 —— 自动生成 config.yml（数据库未就绪时本次启动失败属预期，文件已生成）
java -jar dreamport-server-1.0.0.jar

# ③ 编辑 config.yml（[必改]：spring.datasource 数据库、jwt-secret、server-token、SMTP）
nano config.yml

# ④ 再次启动 → 打开 http://域名:18898/setup 完成初始化向导
java -jar dreamport-server-1.0.0.jar
```

向导完成两件事：**创建管理员账号**（写入管理员名单）+ **选择部署方式**（全新部署 / 上传旧库 .sql 导入）。

> **优先级**：命令行参数 > 环境变量（WL_JWT_SECRET 等，Docker/CI 可用）> config.yml > 内置默认。
> ⚠️ `config.yml` 含密钥，已列入 .gitignore，**不要提交或外传**。

### 2.3 进程守护（可选）

**systemd（推荐）**：`deploy/dreamport-server.service` 无需环境变量，只需确认路径：

```bash
sudo cp dreamport-server-1.0.0.jar /opt/dreamport/
sudo cp deploy/dreamport-server.service /etc/systemd/system/
sudo systemctl enable --now dreamport-server
```

**Docker**：把 config.yml 挂载进容器即可：

```bash
docker build -t dreamport .
docker run -d --name dreamport \
  -p 18898:18898 -p 18899:18899 \
  -v /opt/dreamport/config.yml:/app/config.yml \
  -v dreamport-docs:/app/docs -v dreamport-uploads:/app/static/uploads \
  dreamport
```

### 2.4 部署后检查

1. `curl http://127.0.0.1:18898/api/health` → `{"status":"ok",...}`（`virtualThread:true`）
2. 浏览器打开首页，注册一个自己的账号
3. 管理后台 `http://域名/admin`：先到 **系统配置** 把 `admins`（管理员玩家名列表）和 `adminNotifyEmail`（问卷提醒邮箱）配好
4. WebSocket 18899 反代需支持 Upgrade；SSE（聊天/问卷流）关闭反代缓冲

---

## 3. 后端配置说明

配置分三层（详细键位见 `config_help_zh.yml` 与生成的 `config.yml` 注释）：

**① 工作目录 config.yml** —— 部署配置（改后重启）：
端口、WebSocket 端口、JWT 密钥、服务器令牌、MySQL 连接、SMTP、LLM 评分、邀请规则、迁移路径。**这是部署唯一需要编辑的文件。**

**② 管理后台（存数据库 dp_setting，热生效）** —— 站点内容：
门户（名称/轮播/团队/时间线/ICP）、背景与公告、管理员名单、通知邮箱、维护模式、下载中心、AstrBot 令牌。

**③ 环境变量 / 命令行** —— 可选覆盖（Docker/CI/临时调试）。

> 原则：**凭据进 config.yml 或环境变量（不进 git），内容进管理后台。**

**问卷 AI 评分示例**（可选功能，不启用时文本题按长度降级评分）：

```yaml
wl:
  llm:
    enabled: true
    api-base: https://api.deepseek.com/v1
    api-key: sk-xxxx
    model: deepseek-chat
```

---

## 4. 插件安装与角色

同一个插件 JAR（`dreamport-plugin-x.y.z.jar`），通过 `role` 区分三种用法：

### primary（主服）

```yaml
# plugins/DreamPort/config.yml
role: primary
backend:
  url: "http://127.0.0.1:18898"     # 后端与主服同机则 localhost；异机填内网 IP
  server-id: "main"
  server-token: "与后端 WL_SERVER_TOKEN 相同"
check:
  fail-policy: cache                # 后端宕机兜底：cache（用最近缓存）| allow | deny
  cache-ttl-seconds: 60
features:
  enforce-whitelist: true           # 进服白名单拦截
  forward-chat: true                # 游戏聊天 → 网页聊天室
  report-join-quit: true            # 进出服播报
web-register-url: "https://你的域名"
```

装好重启即生效。进服拦截逻辑：approved 放行；未注册/pending/审核中/被封禁 → 踢出并提示注册地址；维护模式（后台开关）放行 OP。

### secondary（子服，替代旧版 Bridge）

子服上同配置，改 `role: secondary`、`server-id: "survival"` 等唯一 ID。子服不再拦截登录，仅做心跳上报（在线人数/玩家列表进后台与状态页）。

### proxy（Velocity 代理端）

Velocity 代理上安装**专用插件** `dreamport-plugin-proxy-1.0.0.jar`（不是 Paper 版！二者不可混装）。
首次启动自动生成 `plugins/dreamport-proxy/config.properties`：

```properties
backend.url=http://127.0.0.1:18898
backend.server-id=proxy
backend.server-token=与后端一致
enforce-whitelist=true
check.fail-policy=cache
```

功能：代理端统一白名单拦截（后端 login-check）+ 60 秒决策缓存 + fail_policy 兜底 + 心跳上报。
Paper 子服从装 `dreamport-plugin-1.0.0.jar` 并设 `role: secondary`（本地不再拦截，由代理统一校验）。

### 校验连通

游戏内执行 `/xmw status`：显示角色、后端地址、连通状态。`/xmw reload` 热重载配置。

---

## 5. 旧版数据迁移

如果服务器此前使用旧版 XMWhitelist（MySQL 存储在某个库里），**接入即迁移**：

1. 把 DreamPort 的 MySQL 指向**旧版所在的同一个库**（例如旧库 `xmc`）
2. 启动后端（`--spring.profiles.active=mysql`）
3. 启动时自动执行：
   - Flyway 建 `dp_*` 新表（自动 baseline，不影响旧表）
   - 检测到 `xmwhitelist_*` 旧表 → 改名 `legacy_<表>_backup`（原地备份，零拷贝）并按列映射导入
   - 若配置 `wl.migration.legacy-dir`：追加导入 file 模式的 `users.json`/`audits.json`
   - 若配置 `wl.migration.legacy-config`：旧 `config.yml` 的门户/背景/公告/管理员导入后台
3. 日志输出迁移报告（各表行数），并写入后台可查的 `migration.report`

**回滚**（方式 B）：停 DreamPort → 把 `legacy_*_backup` 改回原名 → 换回旧版插件 JAR。旧数据从未被删除。

**实测记录**（v0.2.1 真实生产 dump）：19 用户 / 71 审计 / 8 邀请 / 1672 进服记录，1778 行 1.6s 导入，哈希逐字节保留，错误密码 401；v0.4.0 又以「向导上传 .sql」路径在同数据上复验通过。

> `file` 存储模式的旧服：MySQL 里没有旧表，向导上传不支持 json；改用 `wl.migration.legacy-dir` 指向旧 `plugins/XMWhitelist/` 目录（方式 B）。

---

## 6. 功能使用指南

### 6.1 玩家侧

| 功能 | 入口 | 说明 |
|------|------|------|
| 注册 | 首页 → 白名单 | 用户名（3-16 位字母数字`_-`）+ 邮箱验证码 + 图形验证码；或使用邀请码免验证码注册 |
| 入服问卷 | 注册后引导 | 15 题中英双语；客观题自动计分，文本题 AI 评分；通过线 60 分（后台可调）；**低置信度答案自动转人工复核** |
| MC ID 验证 | 控制台 → ID 验证 | 绑定游戏 ID → 3 分钟内用该 ID 进服一次 → 回网页点"验证"完成绑定（基岩版同理，自动加 `.` 前缀） |
| 邀请码 | 控制台 → 邀请管理 | approved 玩家可生成（默认每人 3 个活跃码、7 天有效）；被邀请人申请 → 邀请人确认 → 进入审核 |
| 个人中心 | `/dashboard` | 状态/资料/头像/换绑邮箱；游戏内余额与时长展示（需插件经济快照） |
| 排行榜 | `/leaderboard` | 财富 / 在线时长 / 活跃天数 |
| 村民族谱 | `/village` | 提交村民交易站坐标（世界/坐标/产出/价格），审核通过后公开展示 |
| 公共机器 | `/machines` | 提交机器（类型/坐标/用途/截图 ≤5MB），审核通过后公开展示 |
| 网页聊天 | 右上角聊天框 | 网页 ↔ 游戏实时互通（SSE），历史保留 500 条 |
| 忘记密码 | 登录页 | 邮箱重置链接，1 小时有效 |

### 6.2 管理员侧（/admin 后台）

| 功能 | 操作 |
|------|------|
| 登录 | 账号需同时在「系统配置 → admins 名单」中（旧版兼容：OP 名单联动待补） |
| 审核管理 | 统一四子标签：**注册审核 / 申诉审核 / 村民审核 / 机器审核**，角标显示待审总数；右上角下载中心、实时 WS 推送新申请 |
| 问卷管理 | 题库 YAML 编辑器 / 重置默认题库 / 查看玩家答题详情与 AI 评语 / 改分；**复核队列**处理 AI 低置信度标记 |
| 门户管理 | 服务器名/简介/轮播图（上传）/管理团队/时间线/特性/ICP |
| 文档管理 | 分类（数字前缀目录）/新建/编辑/删除/排序，Markdown 渲染 |
| 下载中心 | 配置客户端/整合包下载条目 |
| 统计 | 注册趋势/问卷统计/审计日志（导出 CSV/JSON） |
| 维护模式 | 一键开关，**持久化**（重启不丢），开启后非 OP 进服被踢 |
| 系统配置 | 管理员名单、通知邮箱、AstrBot 令牌等 |
| 白名单同步 | `bukkit` 白名单模式时一键把 approved 同步进服务器原生白名单 |

### 6.3 QQ 机器人（AstrBot）对接

1. 管理后台 → 系统配置 → 设置 `astrbotApiToken`
2. 机器人侧脚本以 `X-API-Token` 请求头调用：

```
GET  /api/astrbot/status              服务器状态
GET  /api/astrbot/players             在线玩家
POST /api/astrbot/bind                QQ↔MC 绑定   {"qq":"123","minecraftName":"x"}
POST /api/astrbot/unbind              解绑
GET  /api/astrbot/lookup/qq/{qq}      查绑定
POST /api/astrbot/chat                QQ 消息进服广播 {"sender":"...","message":"..."}
```

---

## 7. 命令与权限

### 游戏内 `/xmw`（插件）

| 命令 | 权限 | 说明 |
|------|------|------|
| `/xmw status` | dreamport.use | 角色/后端地址/连通性 |
| `/xmw reload` | dreamport.admin | 热重载插件配置 |
| `/xmw list` | dreamport.admin | 待审核玩家列表 |
| `/xmw approve\|reject <玩家> [原因]` | dreamport.admin | 审核通过/拒绝 |
| `/xmw ban\|unban <玩家> [原因]` | dreamport.admin | 封禁/解封 |
| `/xmw delete <玩家>` | dreamport.admin | 删除用户（含移出白名单） |
| `/xmw info <玩家>` | dreamport.admin | 查询用户详情 |
| `/xmw version` | dreamport.use | 版本信息 |

权限默认：`dreamport.use` 所有人；`dreamport.admin` OP。所有操作经服务器令牌调用后端并写审计日志。

---

## 8. 常见问题 FAQ

**Q：玩家说"验证服务暂时不可用"进不来？**
后端宕机且 fail-policy 生效。检查后端进程/`/xmw status`；`fail-policy: cache` 下老玩家有缓存可继续玩，未缓存玩家会被拒。紧急时可临时改 `fail-policy: allow`。

**Q：玩家收不到验证码邮件？**
查看后端日志：若显示"日志模式"说明 SMTP 未配置；若报发送失败检查 SMTP 账号/授权码/端口（465 SSL / 587 STARTTLS）。注意邮件有信誉延迟，优先检查垃圾箱。

**Q：老玩家密码还能用吗？**
能。迁移保留旧哈希，玩家用旧密码正常登录，登录成功后自动升级为 bcrypt（无感）。

**Q：管理后台登录报"需要管理员权限"？**
登录账号必须在「系统配置 → admins」名单里。第一位管理员：手动往 `dp_setting`（key=`admins.list`）写入 `["你的玩家名"]`，或用旧 config.yml 迁移自动带入。

**Q：网页打开了但样式/页面 404？**
前端产物没打进 JAR：跑 `./scripts/build.sh`（先构建前端再同步进 static 再打包），不要只 `mvn package`。

**Q：端口冲突？**
18898/18899 被占用时改 `server.port` 与 `wl.ws-port`（注意前端 `api.ts` 与插件 `backend.url` 同步）。

**Q：限流导致测试不便？**
登录 5 次/分钟、注册 3 次/分钟、验证码 3 次/5 分钟（按 IP）。开发环境重启后端即清零；生产不要关。

**Q：如何迁移到新机器？**
导出 MySQL（mysqldump）→ 新机导入 → 拷贝 `docs/`、`static/uploads/`、`email/` 三个运行时目录 → 启动。

---

## 9. 安全清单

- [ ] `WL_JWT_SECRET` 已换成强随机值（不是默认 dev 值）
- [ ] `WL_SERVER_TOKEN` 已更换且与所有插件一致
- [ ] `wl.seed-demo: false`（生产关闭演示账号）
- [ ] `wl.cors.allowed-origins` 收敛为站点域名（不用 `*`）
- [ ] SMTP 密码只存在于环境变量
- [ ] 18899 WebSocket 未暴露公网（或已用反代加鉴权）
- [ ] 数据库定期备份（mysqldump + `static/uploads/` 目录）

---

## 附：文档索引

| 文档 | 内容 |
|------|------|
| `README.md` | 项目简介与架构 |
| `docs/PROJECT_DOCUMENTATION.md` | 完整项目文档（设计视角） |
| `docs/IMPLEMENTATION_PROGRESS.md` | 功能进度总表 |
| `docs/API_CONTRACT.md` | API 契约（80+ 端点） |
| `config_help_zh.yml` / `config_help_en.yml` | 配置键逐项说明 |
| `AGENTS.md` / `Rules.md` | AI 协作与工程规则 |
| `CHANGELOG.md` | 变更日志 |
