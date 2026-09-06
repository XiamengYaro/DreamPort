# AGENTS.md — AI 协作指南

> 本文件指导 AI 编码代理在本仓库中工作。编码标准与硬性规则见 [Rules.md](Rules.md)。
> 两者冲突时以 Rules.md 为准；本文件负责"怎么干活"。

---

## 1. 项目是什么

**DreamPort · 夏日小镇 · 梦港**（XM DreamPort）—— Minecraft 服务器门户与玩家管理系统。
本仓库是旧版 XMWhitelist 的**全量重写**：

- 架构：独立后端服务（Spring Boot 3.4 / Java 21 虚拟线程）+ Paper 薄插件 + Vue 3 前端
- 品牌结构：夏日小镇（主品牌）· 梦港（项目名）· 夏梦（作者署名）
- 硬性目标：**功能 100% 对标旧版**；旧 MySQL 数据、文件存储、密码哈希**无缝迁移**
- 主分支 `main`；当前版本 `0.1.0-dev`（SemVer 0.x 开发期）

## 2. 仓库地图

```
DreamPort/
├── dreamport-common/    # 插件↔后端协议 DTO（包根 cn.xmcraft.dreamport.common）
├── dreamport-server/    # 独立后端（:18898 REST+SPA / :18899 WebSocket）
├── dreamport-plugin/    # Paper 1.20 薄插件（primary/secondary 双角色）
├── frontend/            # Vue 3（继承旧版前端并升级）
├── docs/                # 设计文档、ADR
└── ../XMWhitelist-Legacy/   # 旧版归档 —— 行为与数据格式的唯一权威参考
```

## 3. 动手前先查的权威参考

| 要做什么 | 去哪查 |
|----------|--------|
| 实现某个功能（注册/问卷/审核/邀请/通知/村谱/公共机器/排行/聊天/门户/文档/AstrBot/Bridge） | Legacy 对应 `web/handler/*`、`service/*` 的**行为**（参数、校验、副作用、状态流转） |
| 数据库兼容 | Legacy `db/Mysql*Dao.java` 的建表/迁移语句；`db/UserData.java` 全字段 |
| 密码兼容 | Legacy `util/PasswordUtil.java`：`$SHA$<base64盐>$<64位hex>`，sha256(盐+密码)，恒时比较 |
| 文件存储兼容 | Legacy `FileUserDao`（users.json，键=用户名的 Map）与 `FileAuditDao`（audits.json 数组） |
| 配置键兼容 | Legacy `config.yml` 全部键路径（门户/问卷/SMTP/LLM/邀请/基岩/代理/AstrBot…） |
| API 契约 | Legacy `frontend/src/services/api.ts`（约 80 个方法）与 `web/ApiRouter.java` |

## 4. 构建与验证

```bash
mvn clean package                          # 后端 + 插件（需 JDK 21 / Maven 3.9+）
cd frontend && npm ci && npm run build     # 前端
java -jar dreamport-server/target/dreamport-server-*.jar   # 冒烟启动
curl http://localhost:18898/api/health     # 健康检查
```

- **提交前必须编译通过**；触碰数据层/迁移必须跑相关测试
- 端口 **18898 / 18899** 与旧版一致，不得擅改
- 冒烟可 `-DskipTests`，但失败的测试不许跳过不报

## 5. Git 工作流

- remote：`origin = http://10.0.0.6:11488/Xiameng/DreamPort.git`（Gitea，凭据在 `~/.git-credentials`，免密推送）
- **每个阶段完成 = 一次提交 + push**，禁止巨型混合提交
- 提交信息：`type(scope): 摘要`（正文中文说明）；type ∈ feat / fix / chore / docs / refactor / test / build
- 首个可运行构建通过 → 打 `v0.1.0` 标签 + `git push --tags`
- 严禁入库：真实密码/API Key/令牌、`application-local.yml`、`target/`、`node_modules/`（.gitignore 已覆盖，但仍需自查）

## 6. 代码来源红线（法律与谱系）

1. **禁止复制任何第三方 GPL 项目的代码**——保持本项目独立谱系
2. Java 后端/插件：对 Legacy 只作**行为规范**参考（读行为 → 写原创实现），禁止整段搬运代码
3. `frontend/` 例外：允许整体继承旧版前端并升级（自研代码，无谱系问题）

## 7. 工作节奏

1. 开工前：读 README 路线图 → 定位当前阶段（P1–P8）→ 查第 3 节对应参考
2. 实现中：遵守 Rules.md；兼容契约拿不准就先翻 Legacy 再写
3. 阶段收尾：编译+冒烟 → 更新 README 路线图状态与 CHANGELOG → commit + push
4. 汇报如实：测试失败、跳过的步骤必须明说，不粉饰

## 8. 行为准则

- 先读后写：改任何文件前先看现状；不覆盖不了解的内容
- 最小改动：不顺手重构无关代码；发现的问题记录到 CHANGELOG 或单独提交
- 破坏性操作（删数据、改 Schema、force push）必须先与用户确认
- 遇到 Legacy 行为明显是 bug 的：默认按 Rules.md 第 9 节的修复清单执行，拿不准的列出来问
