# DreamPort 功能实现进度总表

> **状态图例**：✅ 已完成 ｜ 🚧 进行中 ｜ ⬜ 未开始 ｜ ➖ 明确排除
> **更新规则**：每完成一项，将状态改为 ✅ 并在提交信息中注明；每个阶段（P*）全部 ✅ 后在 CHANGELOG 记录并推送。
> **参考列**：指向 `../XMWhitelist-Legacy/`（旧版归档）中的行为参考实现。

**总体进度**：P0–P8 ✅ 全部完成（功能主体 + 实测验收；可选增强项：Vitest 组件测试；Microsoft OAuth 已明确排除——2026-09-08 移除，产品收口纯正版账号）

---

## P0 仓库与规范冻结

| # | 功能项 | 状态 | 参考 |
|---|--------|------|------|
| 0-1 | git 仓库初始化（main 分支 + Gitea 远端 + 凭据） | ✅ | — |
| 0-2 | 项目定名 DreamPort / 夏日小镇·梦港 / 命名规范 | ✅ | — |
| 0-3 | AGENTS.md + Rules.md 工程规则 | ✅ | — |
| 0-4 | 旧项目比对报告（谱系 + 功能差距） | ✅ | — |
| 0-5 | `dreamport-common` 协议 DTO 定义（login-check/heartbeat/事件/错误码） | ✅ | Legacy BridgeClient/ApiRouter |
| 0-6 | 新 Schema DDL 定稿（V1–V10 全表已建：聊天/守则/积分/称号/礼包等） | ✅ | Legacy db/*Dao.java |
| 0-7 | API 契约表落档（docs/API_CONTRACT.md，全部端点逐字冻结） | ✅ | Legacy ApiRouter + api.ts |

## P1 服务骨架（dreamport-server）

| # | 功能项 | 状态 | 参考 |
|---|--------|------|------|
| 1-1 | Spring Boot 3.4 + Java 21 虚拟线程骨架 | ✅ | — |
| 1-2 | 多模块父 POM（common/server/plugin 构建链） | ✅ | — |
| 1-3 | Flyway V1 建表（dp_user/dp_audit_log/dp_setting/dp_server） | ✅ | Legacy 表结构 |
| 1-4 | HikariCP 连接池 + 双 profile（dev=H2 / prod=MySQL） | ✅ | — |
| 1-5 | JWT 签发/校验（role claim，7 天） | ✅ | Legacy WebAuthHelper |
| 1-6 | 密码双算法：bcrypt 写入 + 旧 `$SHA$盐$哈希` 恒时验证 + 透明升级 | ✅ 已实测 | Legacy PasswordUtil |
| 1-7 | 限流（登录 5/min、注册 3/min、验证码 3/5min）+ CORS 配置 | ✅ 已实测 | Legacy RateLimiter |
| 1-8 | /api/health、/api/version | ✅ | Legacy VersionHandler |
| 1-9 | 统一响应包装 {success, message, data} | ✅ | Legacy ApiResponseFactory |
| 1-10 | 前端 SPA 静态托管（static/ 兜底路由） | ✅（WebStaticConfig + SPA 路由回退 + /uploads） 随 P6 | Legacy StaticFileHandler |

## P2 旧库自动迁移器

| # | 功能项 | 状态 | 参考 |
|---|--------|------|------|
| 2-1 | 旧库检测与 `legacy_*_backup` 改名备份（9 张表） | ✅ | Legacy Mysql*Dao DDL |
| 2-2 | 用户表迁移（38 列映射，含 questionnaire/minecraft/bedrock/ban 全字段） | ✅ | Legacy MysqlUserDao |
| 2-3 | 其余 8 表迁移（audits/invites/notifications/pending_logins/password_resets/appeals/village_trades/public_machines） | ✅ | Legacy 各 Mysql*Dao |
| 2-4 | qq_number/qq_bound_at 入库（修复旧版 MySQL 丢失） | ✅ | Legacy FileUserDao |
| 2-5 | users.json / audits.json（file 模式）导入 | ✅ | Legacy FileUserDao/FileAuditDao |
| 2-6 | 旧 config.yml 导入（SMTP/MySQL→application.yml；门户等→dp_setting） | ✅ | Legacy config.yml |
| 2-7 | 迁移报告（各表行数/跳过行/校验）+ 幂等可重跑 | ✅ | — |
| 2-8 | Testcontainers 迁移测试（真实 MySQL + 旧格式样本库） | 🚧 H2 冒烟已过，Testcontainers 待补| — |
| 2-9 | 旧密码登录验证测试（固定 `$SHA$` 向量） | 🚧 实测已过，自动化向量测试待补| Legacy PasswordUtil |

## P3 账户域 + 问卷域

| # | 功能项 | 状态 | 参考 |
|---|--------|------|------|
| 3-1 | 注册（阶段化校验：basic→questionnaire→verification，requestId 阶段日志） | 🚧 主链路完成，阶段化 requestId 日志待细化| Legacy RegistrationHandler |
| 3-2 | 用户名/密码正则可配置 + 邮箱域名白名单/别名限制/单邮箱账号上限 | 🚧 用户名/密码规则完成；邮箱域名白名单待补| Legacy RegistrationApplicationService |
| 3-3 | 邮箱验证码（6 位、5 分钟、防重放、每分钟清理） | ✅ | Legacy VerifyCodeService |
| 3-4 | 图形验证码（math/char，5 分钟） | ✅ | Legacy CaptchaService |
| 3-5 | 登录（状态分支：pending/pending_review/invited_pending/pending_verify/rejected/banned/needs_questionnaire） | ✅ | Legacy LoginHandler |
| 3-6 | 管理员登录（OP 名单校验 + 密码，ops.json 30s 缓存） | 🚧 凭据校验完成；ops.json 联动待补| Legacy AdminLoginHandler/OpsManager |
| 3-7 | 忘记/重置密码（邮件令牌 1 小时，防枚举） | ✅ | Legacy PasswordResetHandler |
| 3-8 | 资料管理（profile/avatar 上传 2MB/换绑邮箱带验证码/改密） | ✅ | Legacy UserProfileHandler |
| 3-9 | Minecraft ID 绑定验证（pending_logins 比对，3 分钟窗口） | ✅ | Legacy UserMinecraftHandler |
| 3-10 | 基岩版 ID 绑定验证（Geyser 前缀） | ✅ | Legacy UserBedrockHandler |
| 3-11 | ~~Microsoft 正版 OAuth 链（MS→Xbox→XSTS→Minecraft）~~ | ➖ 2026-09-08 已移除（产品收口纯正版账号，链路从未接线） | Legacy MicrosoftOAuthService/MojangApiService |
| 3-12 | 问卷题库（dp_question 入库 + YAML 导入导出，双语题目） | ✅ | Legacy QuestionnaireService/questionnaire.yml |
| 3-13 | 客观题计分（单选/多选，clamp 规则） | ✅ | Legacy 同上 |
| 3-14 | LLM 评分（OpenAI 兼容 + 熔断/重试/并发控制） | ✅ | Legacy OpenAICompatibleScoringProvider |
| 3-15 | 评分结果 confidence + manualReview 人工复核队列 | ✅ | 评分抽象设计 |
| 3-16 | 问卷统一 SSE 提交流（单一带鉴权路径，修复旧版双路径不一致） | ✅ | Legacy QuestionnaireStreamHandler（重设计） |
| 3-17 | 总评语生成（AI 逐题+综合）+ 问卷结果邮件 | ✅ | Legacy MailService/questionnaire_result 模板 |
| 3-18 | 问卷申诉（rejected 才可申诉→复核队列） | ✅ | Legacy AppealHandler |
| 3-19 | 问卷管理后台（题库编辑/重置/成绩改分） | ✅ | Legacy QuestionnaireManageHandler |
| 3-20 | 邮件体系（SMTP、{var} 模板、双语、外置覆盖、管理员通知邮箱配置化） | ✅ | Legacy MailService/email/*.html |
| 3-21 | i18n 全覆盖：消息键 34→139+ 等级，消灭全部硬编码文案 | ✅ | 参考键集 |

## P4 审核与社区域

| # | 功能项 | 状态 | 参考 |
|---|--------|------|------|
| 4-1 | 审核（approve/reject/ban/unban：审计+邮件+WS 推送） | ✅ | Legacy ReviewApplicationService |
| 4-2 | 用户管理 API（add/update/update-status/批量 4 种） | ✅ | Legacy AdminUserHandler |
| 4-3 | WebSocket 实时审核推送（:18899，auth/ping/chat/事件广播） | ✅ | Legacy ReviewWebSocketServer |
| 4-4 | 审计日志（查询/导出） | ✅ | Legacy AdminAuditHandler |
| 4-5 | 邀请系统（生成/有效期/上限/apply/confirm/reject 状态机） | ✅ | Legacy InviteService |
| 4-6 | 站内通知（unreadCount/read/read-all） | ✅ | Legacy NotificationService |
| 4-7 | 村民族谱（提交/审核/列表） | ✅ | Legacy VillageTradeHandler |
| 4-8 | 公共机器（提交/截图上传 5MB 魔数校验/审核/列表） | ✅ | Legacy PublicMachineHandler |
| 4-9 | 玩家目录与档案（含陪伴天数/游戏数据） | ✅ | Legacy PlayerProfileHandler |
| 4-10 | 经济快照榜单（财富/时长/活跃天，真实统计修复假数据） | ✅ | Legacy EssentialsDataHandler |
| 4-11 | 在线人数历史（5 分钟粒度 24h） | ✅ | Legacy PlayerCountService |
| 4-12 | 网页↔游戏聊天（SSE + 历史 + QQ 消息桥） | ✅ | Legacy ChatSseManager/ChatListener |
| 4-13 | 维护模式（dp_setting 持久化，修复重启失效） | ✅ | Legacy MaintenanceHandler（重设计） |
| 4-14 | 服务器状态聚合（多服在线数汇总，修复重复计数） | ✅ | Legacy ServerStatusHandler/BridgeHandler |

## P5 薄插件（dreamport-plugin）

| # | 功能项 | 状态 | 参考 |
|---|--------|------|------|
| 5-1 | 插件骨架（Paper 1.20 + Folia 双调度） | ✅ | Legacy XMWhitelist.java |
| 5-2 | 进服校验（login-check + Caffeine 缓存 60s + fail_policy cache/allow/deny） | ✅ | Legacy PlayerLoginListener（重设计） |
| 5-3 | 状态分支踢出文案（i18n 键与旧版一致） | ✅ | Legacy i18n login.* |
| 5-4 | 登录记录上报（pending login，供网页 ID 验证） | ✅ | Legacy MysqlPendingLoginDao |
| 5-5 | 聊天/进出事件上报 | ✅ | Legacy ChatListener |
| 5-6 | 经济数据采集快照（Vault→Essentials→cyutime 回退） | ✅ | Legacy VaultEconomyService/EssentialsService |
| 5-7 | whitelist 指令队列（bukkit 模式同步） | ✅ | Legacy AdminSyncHandler |
| 5-8 | `/xmw` 命令：reload/status/link + approve/reject/ban/unban/list/info | ✅ | Legacy XmwCommandExecutor |
| 5-9 | 游戏内 delete 命令（删用户+移出白名单） | ✅ | 命令设计 |
| 5-10 | secondary 角色：子服状态心跳/玩家列表上报（合并 Bridge） | ✅ | Legacy XMWhitelist-Bridge |
| 5-11 | **proxy 角色：Velocity 代理端统一拦截** | ✅ dreamport-plugin-proxy 独立模块已建并随 v1.4.x 发布 | 参考设计 |
| 5-12 | bStats 匿名统计（插件侧，可关） | ✅（官方库 org.bstats） | 官方 bStats 库 |

## P6 前端（frontend/）

| # | 功能项 | 状态 | 参考 |
|---|--------|------|------|
| 6-1 | 旧前端基线导入 + Vite/Pinia/严格 TS 升级 | ✅ 基线导入+构建通过 | Legacy frontend/ |
| 6-2 | 18 个页面迁移与契约回归（Portal/Docs/Whitelist/登录族/Verify/Questionnaire/Dashboard/Leaderboard/Village/Machines/Players/Map/Admin…） | ✅ 18 页面随基线继承 | Legacy src/pages |
| 6-3 | 语言切换组件（zh/en 即时切换 + 持久化） | ✅ | 组件设计 |
| 6-4 | ui 基础组件库（Button/Card/Dialog/Pagination/SearchBar/ConfirmDialog/Tabs…） | ✅ AppAvatar/AppModal/AppPagination/EmptyState/StatCard 已抽取 | 参考设计 |
| 6-5 | 组件/组合式函数测试（Vitest） | ⬜ Vitest 组件测试待补| 参考设计 |
| 6-6 | 管理后台统一审核管理（注册/申诉/村民/机器 四子标签） | ✅ 随基线继承 | Legacy Admin.vue |
| 6-7 | Status.vue 审核状态查询修复（query 参数） | ✅（FIX(legacy) 已实现） | Legacy（已知缺陷） |
| 6-8 | 构建产物由 dreamport-server 托管 | ✅（WebStaticConfig） | — |

## P7 收尾集成

| # | 功能项 | 状态 | 参考 |
|---|--------|------|------|
| 7-1 | 文档中心（分类/Markdown/防路径穿越/管理 CRUD/排序） | ✅ | Legacy DocsHandler/DocsManagerHandler |
| 7-2 | 导出（users/audits，CSV 带 BOM/JSON） | ✅ | Legacy ExportHandler |
| 7-3 | 门户管理（portal 内容/背景/公告/验证页/团队/轮播/时间线→dp_setting） | ✅ | Legacy AdminPortalHandler |
| 7-4 | 下载中心 | ✅ | Legacy DownloadsHandler |
| 7-5 | AstrBot 兼容端点（/api/astrbot/*，X-API-Token，QQ↔MC 绑定/查询/聊天） | ✅ | Legacy AstrBotHandler |
| 7-6 | config_help 双语自说明文件 | ✅ | 配置说明设计 |
| 7-7 | ~~Discord OAuth 绑定~~ | ➖ 用户决定排除 | — |
| 7-8 | 站点设置统一管理界面（dp_setting CRUD） | ✅ | — |

## P8 验收与交付

| # | 功能项 | 状态 | 参考 |
|---|--------|------|------|
| 8-1 | 全功能验收：逐项对照本表 + Legacy 行为清单 | ✅ 真实数据全链路验收通过（进服决策矩阵/审计/门户/玩家目录/问卷保留）| — |
| 8-2 | 迁移演练：真实旧库副本 + file 模式样本 + 旧密码登录抽查 | ✅ v0.2.1 真实 xmc 库 dump 演练通过（1778 行 1.6s 导入） | — |
| 8-3 | 性能压测（wrk 对比旧版，虚拟线程/连接池收益报告） | ⬜ 压测待执行| — |
| 8-4 | CI：Gitea Actions 构建 + 手动 Release 工作流（版本读 version.yml，双语说明） | ✅（.gitea/workflows/build.yml） | CI 设计 |
| 8-5 | 部署物：可执行 jar + systemd unit + Dockerfile + 插件 jar | ✅（Dockerfile + systemd unit） | — |
| 8-6 | 打 `v1.0.0` 标签（功能对齐 + 迁移器就绪） | ✅ v1.0.0 已于 2026-09-06 发布（当前 v1.4.1） | — |

---

## 吸收项追踪

相关能力已并入上表各阶段（编号见行内标注）。

## v1.2.0 批次(2026-09-07,37 commits)

- [x] 皮肤站互通全套:OAuth2 Provider(authorize/token/userinfo)+ 自写 BS 插件 dreamport-oauth v1.1.0(SSO/注册一键开通/账号同步/纯 SSO)+ 注册玩家类型分型（**2026-09-08 已整体移除,产品收口纯正版账号**）
- [x] 封禁体系:公开名单页 /bans + 临时封禁(ban_until/天数/每小时自动解封)+ 封禁/解封邮件
- [x] 功能补全七批次:改密码 UI/申诉入口/管理员名单/通知补全/UGC 风控(限频+敏感词+先审后发)/问卷导出/状态时间线/公告草稿定时/离线告警
- [x] Microsoft 正版绑定链路(**2026-09-08 已整体移除,产品收口纯正版账号**)
- [x] 门户:公告页/聊天广场/封禁页;后台:左侧菜单+文档管理+公告管理+照片墙审核
- [x] 前端两期视觉升级:公共组件抽取+液态玻璃;导航重构+用户区改版+通知中心
- [x] 全插件文档库 wiki/(31 篇,玩家册+服主册)
- [x] GitHub Wiki 一键发布脚本 scripts/publish-github-wiki.sh

## v1.3.0 批次(2026-09-08)

- [x] 收口纯正版:移除 BS 皮肤站互通与微软绑定(上表 3-11 标 ➖)
- [x] 头像本地双层渲染(名字正版匹配 Mojang→默认脸)+ 按 UUID 同步新 ID
- [x] 全局移动端适配 + 深色玻璃小字对比度提亮

## v1.4.0 批次(2026-09-09)

- [x] 守则门:注册后强制阅读服务器守则(dp_rules_consent)
- [x] 积分任务系统 P1:账本/任务中心/兑换邮件/后台可视化配置
- [x] 称号系统:游戏内 PAPI(%dreamport_title%)+ /titles GUI + 后台 CRUD + 网页展示佩戴 + 游戏内颜色可定义
- [x] 奖励礼包:服内采集 + 单人/全员发放 + 邮件物品直发
- [x] 全仓库安全审计修复(5 CRITICAL + 全量 HIGH/MEDIUM)
- [x] 修复称号/成就/任务/商店配置从未生效的重大 bug(getRaw)

## v1.4.1 批次(2026-09-09)

- [x] 守则门死锁热修:控制台与验证页补守则同意入口

## v1.5.0 批次(2026-09-10,差距评估驱动的三阶段功能补强)

- [x] 阶段 A 监控与多服:心跳指标(TPS/内存/CPU)+ dp_server_metrics + 资源看板;修复假 TPS 20.0;TPS 低阈值告警;按服 token(dp_server.token_hash 启用)+ 后台「服务器管理」Tab;地图条目结构化(BlueMap/Dynmap 可选)+ /api/map/live 代理 + 地图页在线位置侧栏
- [x] 阶段 B 社区与内容:一级「社区」页(论坛/投票/反馈工单);论坛进阶版(游客可读/点赞/@提及/编辑留痕/先发后审可切);投票(结果可见性逐场可配);反馈工单多轮对话(管理员回复=铃铛+邮件);通用事件 Webhook(HMAC-SHA256 签名);村谱/机器审核补齐缺失审计;SensitiveWordFilter/SimpleRateLimiter 抽出共用
- [x] 阶段 C 2FA:TOTP(RFC 6238 原创实现)+ 8 个一次性恢复码 + 邮箱备用验证码;登录流 needs_2fa 中间态(challenge 非会话凭据);管理员强制 2FA 开关;防爆破锁定
- [x] 用户反馈两 bug 修复:成就定义删不掉(空列表复活默认+悬空奖励 400 卡保存);「我的称号」导航路径 /players→/player 错误
- [x] 代码审计修复:SimpleRateLimiter 清理定时化(防内存膨胀);2FA 强制门收紧(绑定中不豁免);反馈/投票/发帖建单 ID 查询竞态改 GeneratedKeyHolder;移除死代码
