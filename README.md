# DreamPort · 夏日小镇 · 梦港

> **XM DreamPort** —— Minecraft 服务器门户与玩家管理系统，夏日小镇官方门户。
>
> **品牌结构**：夏日小镇（主品牌）· 梦港（项目名）· 夏梦（作者署名 by XiaMeng / XM）
> "梦港" = DreamPort 直译——夏梦（XiaMeng → **XM**）之"梦" + Port（**Portal 门户** / **Harbor 港湾**）。
> 玩家在梦港查验入镇、停靠补给：**进入夏日小镇，先入梦港。**
>
> 独立架构重写，功能对标旧版 XMWhitelist（归档于 `../XMWhitelist-Legacy/`），数据库无缝迁移兼容。

- **当前版本**：`0.1.0-dev`（脚手架阶段）
- **协议**：MIT
- **变更记录**：[CHANGELOG.md](CHANGELOG.md)

---

## 一、架构简介

重写版将旧版"单插件内嵌 Web 服务"升级为**独立后端 + 薄插件**的三层架构：

```
┌────────────────────────┐            ┌─────────────────────────────────────┐
│  Minecraft 服务器集群    │            │  dreamport-server（独立后端进程）     │
│  ┌──────────────────┐  │   HTTP     │  Spring Boot 3.4 · Java 21          │
│  │ 薄插件 dreamport  │──┼──(内网API)─▶│  虚拟线程 · REST + SPA :18898       │
│  │ -plugin (primary) │  │  +缓存兜底 │  WebSocket :18899（审核实时推送）     │
│  └──────────────────┘  │            │  Flyway 新 Schema · HikariCP        │
│  ┌──────────────────┐  │            │  MySQL                              │
│  │ 子服插件          │──┼──(上报)────▶                                     │
│  │ (secondary)       │  │            └─────────────────────────────────────┘
│  └──────────────────┘  │                        ▲
└────────────────────────┘            浏览器访问 Vue 3 SPA（后端托管）
```

### 模块划分（Maven 多模块，包根 `cn.xmcraft.dreamport`）

| 模块 | 技术 | 职责 |
|------|------|------|
| `dreamport-common` | Java 21 | 插件 ↔ 后端通信协议 DTO、错误码、事件常量 |
| `dreamport-server` | Spring Boot 3.4 + 虚拟线程 | 全部业务：注册/问卷(LLM 评分)/审核/邀请/通知/村谱/公共机器/排行/门户/文档/聊天/AstrBot 对接；托管前端 SPA |
| `dreamport-plugin` | Paper API 1.20 | 薄插件：进服白名单校验（本地缓存 + fail_policy 兜底）、聊天/进出事件上报、经济数据快照、whitelist 指令执行 |
| `frontend/` | Vue 3 + Vite + Pinia | 由旧版前端升级（保留全部页面与双语），构建产物由后端托管 |

### 关键设计决策

| 决策 | 方案 |
|------|------|
| 架构形态 | 独立后端服务 + 薄插件（多服共享一个后台） |
| 运行时 | Java 21 + 虚拟线程（高并发低成本） |
| 数据兼容 | **新 Schema（`dp_` 前缀，规范约束/索引）+ 自动迁移器**：首次启动检测旧库 9 张表 / users.json / audits.json / config.yml，备份改名后导入 |
| 密码兼容 | 旧 `$SHA$盐$哈希` 算法保留可验证，登录成功后透明升级为 bcrypt |
| 前端 | 保留旧版前端代码作为基线，升级技术栈，API 契约尽量逐字保留 |

---

## 二、版本管理规范（语义化版本 SemVer）

遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 与 [Keep a Changelog](https://keepachangelog.com/zh-CN/)：

```
MAJOR.MINOR.PATCH   例：1.2.3
```

- **MAJOR**：不兼容的 API / Schema / 配置变更
- **MINOR**：向后兼容的功能新增
- **PATCH**：向后兼容的问题修复
- **预发布**：`0.x.y-alpha.N` / `-beta.N` / `-rc.N`
- **当前处于 `0.x` 开发期**（API 不稳定）；与旧版功能完全对齐、迁移工具就绪时发布 **`1.0.0`**
- 每次发布：更新 `CHANGELOG.md` + 打 git 标签 `vX.Y.Z`（首个标签将在可运行的脚手架构建通过时打出）

---

## 三、构建要求

| 工具 | 版本 |
|------|------|
| JDK | 21+ |
| Maven | 3.9+ |
| Node.js / npm | 18+（前端） |

```bash
mvn clean package        # 后端 + 插件
cd frontend && npm ci && npm run build   # 前端（产物由 dreamport-server 托管）
```

---

## 四、路线图

> 逐项进度见 [docs/IMPLEMENTATION_PROGRESS.md](docs/IMPLEMENTATION_PROGRESS.md)（128 项功能追踪）。

| 阶段 | 内容 | 状态 |
|------|------|------|
| P0 | 仓库初始化、定名 DreamPort、规范冻结（协议/Schema/API 契约） | ✅ |
| P1 | dreamport-server 骨架（虚拟线程/Flyway/JWT/限流）——旧密码兼容实测通过 | ✅ |
| P2 | 旧库自动迁移器（9 表 + 文件存储 + config 导入，幂等报告） | ✅ |
| P3 | 账户域 + 问卷域（邮箱验证码、LLM 评分+人工复核、统一 SSE、i18n 全覆盖） | ✅ |
| P4 | 审核与社区域（审计/WS 推送/邀请/通知/村谱/公共机器/排行/聊天） | ✅ |
| P5 | dreamport-plugin 完善（缓存 fail_policy/经济快照/三角色/delete 命令/bStats） | ✅ |
| P6 | 前端升级与托管切换（语言切换/SPA 回退/uploads 静态） | ✅ |
| P7 | 收尾（文档中心/导出/维护模式/AstrBot 兼容端点/config_help） | ✅ |
| P8 | 全功能验收 + 迁移演练 + 压测 + CI（Gitea Actions） | 🚧 验收中 |

> 自 参考项目 v1.8.0 的吸收项已并入对应阶段；Discord OAuth 经评估**排除**。依据：[docs/FEATURE_GAP_vs_参考项目.md](docs/FEATURE_GAP_vs_参考项目.md)。

## 五、目录结构

```
DreamPort/
├── dreamport-common/  # （P0）协议 DTO
├── dreamport-server/  # （P1）独立后端
├── dreamport-plugin/  # （P5）Paper 薄插件
├── frontend/          # （P6）前端（由 Legacy 前端升级）
├── docs/              # 设计文档、进度表、比对报告
├── CHANGELOG.md
├── LICENSE
└── README.md
```

## 六、文档索引

| 文档 | 内容 |
|------|------|
| [docs/PROJECT_DOCUMENTATION.md](docs/PROJECT_DOCUMENTATION.md) | **完整项目文档**（架构/数据/API/安全/配置/部署） |
| [docs/IMPLEMENTATION_PROGRESS.md](docs/IMPLEMENTATION_PROGRESS.md) | **功能实现进度总表**（128 项，实时更新） |
| [docs/FEATURE_GAP_vs_参考项目.md](docs/FEATURE_GAP_vs_参考项目.md) | Legacy vs 参考项目 功能差距与吸收决策 |
| [AGENTS.md](AGENTS.md) / [Rules.md](Rules.md) | AI 协作指南 / 工程硬规则 |
| Legacy 归档 | `../XMWhitelist-Legacy/`（含谱系比对报告与旧版 README） |
