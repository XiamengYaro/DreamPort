<div align="center">

<img src="frontend/public/Logo111.png" width="88" alt="DreamPort" />

# DreamPort · 夏日小镇 · 梦港

**Minecraft 服务器门户与玩家管理系统**

网页注册 · 问卷审核 · 玩家管理 · 社区门户 · 一体化部署

[快速开始](#-快速开始) · [功能总览](#-功能总览) · [旧版数据迁移](#-旧版数据一键迁移) · [常见问题](#-常见问题)

`v1.1.0` · `Paper/Velocity 1.20+` · `Java 21` · `MySQL 8`

</div>

---

## 这是什么

DreamPort 为 Minecraft 服务器提供**一整套面向玩家的网站 + 管理员后台**：

- 玩家在**网页**上注册账号 → 答问卷（AI 自动评分）→ 绑定游戏 ID → 进服
- 服主在**管理后台**审核申请、发布公告、管理文档与社区内容
- 游戏内聊天与网页聊天**实时互通**，排行榜/村民族谱/公共机器展示服务器生态

全新架构（独立后端 + 薄插件），与旧版 XMWhitelist 数据**一键迁移、密码无缝兼容**。

```
┌─────────────────────────┐          ┌──────────────────────────────┐
│  Minecraft 服务器        │          │  dreamport-server (独立后端)  │
│                         │  心跳/校验│                              │
│  Velocity 代理 ─────────┼─────────▶│  网页门户 + 管理后台 :18898    │
│  Paper 子服  ×N ────────┼─────────▶│  实时推送 WebSocket  :18899   │
│  (聊天/进出/经济上报)     │          │        │                     │
└─────────────────────────┘          └────────┼─────────────────────┘
                                              ▼
                                     MySQL 8（玩家/问卷/审计）
```

## ✨ 功能总览

### 玩家侧

| | |
|---|---|
| 🖥️ **门户官网** | 服务器介绍、轮播图、团队展示、发展时间线（全部后台可视化配置） |
| 📝 **注册 + 问卷** | 邮箱验证码注册 → 入服问卷（单选/多选/填空/问答，AI 自动评分） |
| 🆔 **ID 验证** | 网页绑定游戏 ID，进服 3 分钟内自动验证（支持基岩版） |
| 💌 **邀请系统** | 老玩家生成邀请码，新人凭码注册，邀请人确认后进入审核 |
| 📊 **排行榜** | 财富 / 在线时长 / 活跃天数（插件自动采集经济数据） |
| 🏘️ **社区内容** | 村民族谱、公共机器展示（提交 → 审核公开展示） |
| 💬 **网页聊天** | 网页 ↔ 游戏实时互通（SSE） |

### 管理侧

| | |
|---|---|
| ✅ **审核工作台** | 注册/申诉/村谱/机器统一审核，实时 WebSocket 推送 |
| 🤖 **AI 评分** | 对接 DeepSeek 等 OpenAI 兼容接口，低置信度自动转人工复核 |
| 📋 **问卷编辑器** | 问卷平台式编辑：四种题型、自动识别 QQ 号/邮箱、整卷保存 |
| 🎨 **门户可视化配置** | Logo/轮播/背景/公告/团队/下载中心，改完即生效 |
| 📦 **数据迁移** | 后台上传旧库 .sql 一键导入（详见下文） |
| 🚧 **维护模式** | 一键开关，持久化不丢失 |
| 📜 **审计日志** | 全部管理操作留痕，支持 CSV/JSON 导出 |

## 🚀 快速开始

只需 **3 个文件**：后端 JAR + 插件 JAR（+ Velocity 版可选）。

### 第一步：启动后端

```bash
# 准备数据库（一次性）
mysql -uroot -e "CREATE DATABASE dreamport CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 首次启动 → 自动生成 config.yml
java -jar dreamport-server-1.1.0.jar
# （数据库未配置时启动失败属预期，文件已生成）

# 编辑 config.yml 后再次启动
nano config.yml && java -jar dreamport-server-1.1.0.jar
```

`config.yml` 中 `[必改]` 项：**数据库连接**、**jwt-secret**、**server-token**（生成随机串：`openssl rand -base64 48`）。所有部署配置集中在这一个文件，无需环境变量。

启动成功会显示字符画 Banner 与中文启动记录（数据库/迁移/初始化状态/各功能开关/访问地址）。

### 第二步：初始化向导

浏览器打开 `http://服务器IP:18898/setup`：

1. **创建管理员账号**（自动写入管理员名单）
2. 选择 **全新部署** 或 **上传旧库 .sql 导入**

完成。现在可以访问官网、登录管理后台了。

### 第三步：安装游戏插件

| 服务器 | 安装文件 | 说明 |
|--------|----------|------|
| **Paper/Folia 子服** | `dreamport-plugin-1.1.0.jar` | 进服拦截、聊天互通、经济采集 |
| **Velocity 代理**（可选） | `dreamport-plugin-proxy-1.1.0.jar` | 代理端统一拦截（用了代理则子服插件设 `role: secondary`） |

插件首次启动自动生成配置，把 `server-token` 改成与后端一致、`backend.url` 指向后端即可。启动控制台会打印**同款字符画 Banner + 中文启动记录**（角色/后端连通/拦截状态一目了然）。

> 📖 详细步骤、systemd/Docker 部署、反向代理配置见 [docs/USER_GUIDE.md](docs/USER_GUIDE.md)

## 🔄 旧版数据一键迁移

从旧版 XMWhitelist 升级，**两种方式任选**：

**方式 A：向导上传（推荐）**

```bash
# 旧服务器上导出
mysqldump -uroot xmc > xmc-backup.sql
```

初始化向导（或管理后台「数据迁移」标签）上传这个 `.sql` 即可。

**方式 B：同库自动迁移** —— 后端直接指向旧版使用的数据库，首次启动自动完成。

迁移能力：

- ✅ 玩家账号（**旧密码原样保留，玩家无感登录**，登录后自动升级加密算法）
- ✅ 问卷成绩与 AI 评语、审计日志、邀请记录、进服记录、申诉、村谱、公共机器
- ✅ 管理员名单、门户内容（旧 config.yml）
- ✅ 旧表自动改名备份（`legacy_*_backup`），**回滚 = 改回表名换回旧 JAR**

> 实测：19 用户 / 1778 行历史数据 1.6 秒完成迁移，错误密码正确返回 401。

## ⌨️ 游戏内命令

```
/xmw status                     查看插件状态与后端连通性
/xmw list                       待审核玩家列表
/xmw approve|reject <玩家>      审核操作
/xmw ban|unban <玩家> [原因]     封禁/解封
/xmw delete <玩家>              删除用户
/xmw reload                     重载配置
```

## ❓ 常见问题

<details>
<summary><b>玩家收不到验证码邮件？</b></summary>

`config.yml` 的 `mail.host` 留空时为日志模式（验证码打印在后端日志）。配置 SMTP 后即可真实发信，注意检查垃圾箱与 SMTP 授权码。
</details>

<details>
<summary><b>后端挂了玩家还能进服吗？</b></summary>

可以配置兜底策略。插件 `check.fail-policy` 设为 `cache`（默认）时用最近的缓存决策，老玩家不受影响；紧急时可改为 `allow`。
</details>

<details>
<summary><b>旧玩家密码还能用吗？</b></summary>

能。迁移保留旧加密格式，玩家用旧密码正常登录，成功后自动升级为 bcrypt，全程无感。
</details>

<details>
<summary><b>管理后台登录后跳回首页？</b></summary>

登录账号必须在「系统配置 → 管理员名单」中。初始化向导创建的账号会自动加入；手动添加成员也在这里操作。
</details>

<details>
<summary><b>更多问题</b></summary>

见 [docs/USER_GUIDE.md §8 常见问题](docs/USER_GUIDE.md) —— 部署、限流、端口、整机迁移等 8 个场景的排查方法。
</details>

## 📦 获取构建

**方式一：下载**（推荐）—— Gitea 仓库的 Actions 构建产物 / Release 附件

**方式二：自行构建**

```bash
./scripts/build.sh        # 前端 + 后端 + 两个插件一次构建
# 产物:
#   dreamport-server/target/dreamport-server-1.1.0.jar
#   dreamport-plugin/target/dreamport-plugin-1.1.0.jar
#   dreamport-plugin-proxy/target/dreamport-plugin-proxy-1.1.0.jar
```

## 🗺️ 路线图

- [x] 独立后端 + 薄插件架构
- [x] 注册/问卷(AI 评分)/审核/邀请/通知全流程
- [x] 村民族谱、公共机器、排行榜、网页聊天
- [x] 旧版数据迁移（后台上传 + 同库自动）
- [x] Velocity 代理端统一拦截
- [x] 双端连接状态实时记录
- [ ] Microsoft 正版 OAuth 绑定（后续小版本）
- [x] `v1.0.0` 正式发布
- [ ] 生产压测（可选增强）

## 📚 文档

| 文档 | 内容 |
|------|------|
| **[使用文档](docs/USER_GUIDE.md)** | 部署、配置、插件、迁移、玩家与管理员指南、FAQ |
| [完整项目文档](docs/PROJECT_DOCUMENTATION.md) | 架构设计、数据模型、API 契约 |
| [功能进度表](docs/IMPLEMENTATION_PROGRESS.md) | 128 项功能逐项实现状态 |
| [变更日志](CHANGELOG.md) | 每个版本的详细变更 |
| [运维检查清单](docs/OPERATIONS_CHECKLIST.md) | 部署核对、日常运维、日志速查 |

---

## 🤖 AI 辅助声明

本项目在开发过程中使用了 AI 编程助手（ZCode / GLM）辅助完成：架构设计讨论、代码实现、问题排查与文档撰写。所有代码均经人工审核、测试与验收，产品方向与全部设计决策由项目作者（Xia_Meng_）确定。

---

<div align="center">

**DreamPort** · MIT License · 由 Xia_Meng_ 为夏日小镇服务器打造

进入夏日小镇，先入梦港。

</div>
