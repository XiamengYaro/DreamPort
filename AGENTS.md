# AGENTS.md — AI 协作指南

> 本文件指导 AI 编码代理在本仓库中工作。编码标准与硬性规则见 [Rules.md](Rules.md)。
> 两者冲突时以 Rules.md 为准；本文件负责"怎么干活"。

---

## 1. 项目是什么

**DreamPort · 夏日小镇 · 梦港**（XM DreamPort）—— Minecraft 服务器门户与玩家管理系统。
本仓库是旧版 XMWhitelist 的**全量重写**：

- 架构：独立后端服务（Spring Boot 3.4 / Java 21 虚拟线程）+ Paper 薄插件 + Vue 3 前端
- 品牌结构：夏日小镇（主品牌）· 梦港（项目名）· 夏梦（作者署名）
- 重写阶段目标（功能 100% 对标旧版 + 旧 MySQL 数据、文件存储、密码哈希无缝迁移）已于 v1.0.0 达成；**现进入新功能开发阶段，不再对标 Legacy**
- 主分支 `main`；当前版本 `1.2.0`（2026-09 封禁体系发布）
- **产品定位（2026-09-08 收口）：只做正版账号**——BlessingSkin 皮肤站互通、微软正版绑定、注册玩家分型已整体移除；头像为本地双层渲染（名字正版匹配 Mojang→默认脸）

## 2. 仓库地图

```
DreamPort/
├── dreamport-common/    # 插件↔后端协议 DTO（包根 cn.xmcraft.dreamport.common）
├── dreamport-server/    # 独立后端（:18898 REST+SPA / :18899 WebSocket）
├── dreamport-plugin/    # Paper 1.20 薄插件（primary/secondary 双角色）
├── frontend/            # Vue 3（继承旧版前端并升级）
├── docs/                # 设计文档、ADR
└── ../XMWhitelist-Legacy/   # 旧版归档（仅旧数据迁移/兼容维护时参考）
```

## 3. 旧版 Legacy 的参考范围（v1.0.1 起）

**项目已进入新功能开发阶段，不再对标旧版 XMWhitelist**：新功能的契约与行为自行设计，与仓库既有实现保持一致即可（响应包装、鉴权通道、`dp_` 前缀等硬规范仍见 Rules.md）。

仅以下场景才需要翻 `../XMWhitelist-Legacy/`：

- 修复旧版数据迁移器（同库自动 / 后台上传 .sql / users.json+audits.json 文件导入）的兼容缺陷
- 排查涉及旧数据格式的遗留问题：旧密码哈希 `$SHA$<base64盐>$<64位hex>`、旧 `config.yml` 键路径、旧表结构

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

- 远端：`origin` = Gitea（http://10.0.0.6:11488/Xiameng/DreamPort.git，主仓）与 `github` = GitHub（github.com/XiamengYaro/DreamPort，镜像，凭据在 macOS 钥匙串）
- 推送：**默认只推 Gitea**（`git push origin <branch> [--tags]`）；**仅当用户明确要求时**才推 GitHub 镜像（`git push github ...`）
- 分支：**新功能开发一律在 `dev` 分支进行**（提交并推送到 `origin/dev`）；**测试验证无问题后**才合并回 `main`，合并后按需打标签发布
- **每个阶段完成 = 一次提交 + push**，禁止巨型混合提交
- 提交信息：`type(scope): 摘要`（正文中文说明）；type ∈ feat / fix / chore / docs / refactor / test / build
- 版本发布：更新 CHANGELOG → 打 `vX.Y.Z` 标签 → 推送（默认仅 Gitea）
- 严禁入库：真实密码/API Key/令牌、`application-local.yml`、`target/`、`node_modules/`（.gitignore 已覆盖，但仍需自查）

## 6. 代码来源红线（法律与谱系）

1. **禁止复制任何第三方 GPL 项目的代码**——保持本项目独立谱系
2. Java 后端/插件：新功能全部原创实现；确需参考 Legacy 行为时同样只作行为规范（读行为 → 写原创），禁止整段搬运代码
3. `frontend/` 例外：允许整体继承旧版前端并升级（自研代码，无谱系问题）

## 7. 工作节奏

1. 开工前：读 README 路线图与 CHANGELOG 了解现状（阶段 P1–P8 已全部完成）
2. 实现中：遵守 Rules.md；新增 API 沿用统一响应包装与既有鉴权通道，前端调用同步进 `services/api.ts`
3. 阶段收尾：编译+冒烟 → 更新 README 路线图状态与 CHANGELOG → commit + push
4. 汇报如实：测试失败、跳过的步骤必须明说，不粉饰

## 8. 行为准则

- 先读后写：改任何文件前先看现状；不覆盖不了解的内容
- 最小改动：不顺手重构无关代码；发现的问题记录到 CHANGELOG 或单独提交
- 破坏性操作（删数据、改 Schema、force push）必须先与用户确认
- 遇到迁移器/旧数据行为问题的：默认按 Rules.md 第 9 节的修复清单执行，拿不准的列出来问
