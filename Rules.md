# Rules.md — 工程规则（硬性标准）

> 本文件是本仓库的编码与工程硬规则。工作流程见 [AGENTS.md](AGENTS.md)。
> 违反本文件中"必须/禁止"条款的代码不得合入 main。

---

## 1. 版本与发布

- 严格遵循 [SemVer 2.0.0](https://semver.org/lang/zh-CN/)：`MAJOR.MINOR.PATCH` + 预发布段 `-alpha/-beta/-rc`
- `0.x` = 开发期（API/Schema 可变）；**`1.0.0` = 功能对齐旧版 + 迁移器就绪** 才允许打出
- 面向用户的每个变更必须记入 [CHANGELOG.md](CHANGELOG.md)；发布 = CHANGELOG + git 标签 `vX.Y.Z` + push（含 `--tags`）

## 2. Java（后端与插件）

- Java 21；IO 密集路径（HTTP/DB/邮件/LLM）必须运行在虚拟线程上
- 优先使用 `record` / `sealed` / 模式匹配；**新代码禁用 Lombok**
- 包命名：`cn.xmcraft.dreamport.{common|server|plugin}.*`，全小写
- 模块依赖单向：`common` 不依赖 server/plugin；`plugin` 只依赖 `common`，禁止引用 server 内部类
- server 内部分层：`api/`（控制器）→ `domain/`（业务）→ `storage/`（仓储）；控制器内不写 SQL、业务内不碰 HttpServletRequest

## 3. Web API 契约

- 对外 `/api/**` 的路径、方法、请求/响应结构与旧版**逐字保持**（前端 `api.ts` 才能少改）；有意修复的偏差必须：在代码注释标 `// FIX(legacy)` + CHANGELOG 记录
- 响应统一包装：`{"success": bool, "message": string, "data"?: ...}`（兼容旧前端的 `msg/message` 双键读法）
- 限流（IP 维度，可配置）：登录 5 次/分钟、注册 3 次/分钟、验证码 3 次/5 分钟；超限 429
- CORS：dev 默认 `*`；生产必须显式配置（`web.allowed_origins`）

## 4. 数据库

- 新表统一前缀 **`dp_`**；字符集 utf8mb4；主键 `id BIGINT AUTO_INCREMENT` + 业务唯一键（如 `uk_user_username`）
- 时间戳一律 `BIGINT` 毫秒 epoch（与旧版一致，前端/迁移都按此假设）
- **所有 DDL 只走 Flyway**（`db/migration/{vendor}/V{n}__desc.sql`），禁止启动时代码里 CREATE/ALTER（迁移器除外）
- 自动迁移器契约：检测旧库 → `RENAME TABLE <旧> TO legacy_<旧>_backup` → 导入（**旧密码哈希原样保留**）→ 输出各表行数报告；必须幂等、可重跑
- 文件模式（users.json/audits.json）导入后与 MySQL 模式行为完全一致；`qq_number/qq_bound_at` 必须入库（旧版丢失点）

## 5. 安全

- 密码：新写入 bcrypt(cost=10)；旧格式 `$SHA$<base64盐>$<hex>` 用**恒时比较**验证，验证成功即透明升级 bcrypt
- JWT：HS256；secret 只来自配置/环境变量（`wl.security.jwt-secret`），禁止默认值上生产；有效期 7 天
- 三套鉴权通道不得混用：用户/管理员 `Authorization: Bearer`（JWT 含 role claim）；服务器间 `/internal/v1/**` 用 `X-Server-Id/X-Server-Token`；AstrBot 用 `X-API-Token`
- 凭据只放 `application-local.yml`（已 gitignore）或环境变量；发现任何真实密钥入库立即轮换并清除历史痕迹前先报告用户

## 6. 调度与异步

- 后端周期任务用 `@Scheduled`（虚拟线程执行器）；插件内用 Bukkit/Folia 双调度（反射探测 `RegionizedServer` 的思路保留自旧版）
- 一切阻塞 IO 不进主线程；插件进服校验必须带本地缓存（approved 名单，TTL 60s 可配）与 `fail_policy`（cache/allow/deny）兜底

## 7. 前端

- Vue 3 组合式 API + TypeScript 严格模式；`services/api.ts` 是唯一接口层，页面禁止裸 fetch
- `locales/zh.json` 与 `en.json` 必须同步加键；Markdown 渲染必须过 DOMPurify
- 构建产物由 `dreamport-server` 托管；前端页面/路由与旧版保持一一对应

## 8. 国际化与文案

- 双语覆盖：游戏内消息 `messages_zh/en.properties`、前端 `locales/{zh,en}.json`、邮件模板 `{type}_{zh|en}.html`
- 邮件模板占位符风格 `{var}`；支持 `plugins/DreamPort/email/` 外置覆盖 jar 内模板

## 9. 禁止事项（旧版踩坑清单，重写中必须修复而非复刻）

| # | 禁止 | 应该 |
|---|------|------|
| 1 | 硬编码管理员邮箱、GitHub 仓库地址 | 配置化 |
| 2 | 假数据/占位统计（如 chatCount=注册数×100、activeDays 恒 0） | 真实统计或明确移除 |
| 3 | 问卷双提交路径行为不一致 | 统一为一条带鉴权的 SSE 流程 |
| 4 | 每次操作新建数据库连接 / 单连接长连接 | HikariCP 统一池化 |
| 5 | token 存内存重启全失效 | JWT 无状态 + role claim |
| 6 | 维护模式不落盘重启失效 | 入 `dp_setting` 持久化 |
| 7 | Bridge 用 `admins[0]` 明文做 token | 服务器 token（`dp_server` 表，哈希存储） |
| 8 | 同一数据字段有的存储后端写丢（qqNumber） | 统一仓储层，字段全覆盖 |

## 10. 测试

- 迁移器与仓储层：Testcontainers + 真实 MySQL 镜像
- 密码算法：固定测试向量单测（旧 `$SHA$` 格式至少 1 组真实 Legacy 生成向量）
- 提交信息可写 `-DskipTests` 于冒烟打包，但 CI/收尾阶段必须全量跑通；失败测试不许跳过不报
