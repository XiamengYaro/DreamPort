# Changelog

本项目的所有显著变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本管理遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Fixed
- **v0.5.16 完成验证后刷新仍显示等待验证**：
  - verified 改为身份验证语义（minecraftUuid != null），与白名单审核状态解耦
  - 验证成功状态流转：pending_verify → approved；pending 且问卷未启用 → approved
    （验证即完成白名单）；pending 且问卷启用 → 保持 pending（还需答题）
  - 幂等判定放宽：已有 UUID 且无新进服记录 → 「已完成验证」（原要求 approved）

### Fixed
- **v0.5.15 外观设置页消失**：问卷设置卡片模板引用 qnCfg/saveQnSettings，
  但 script 中实际定义为 questCfg（前次编辑锚点未匹配静默失败），
  渲染时抛 ReferenceError 导致整页白屏。已统一命名并补上独立的
  saveQuestSettings 函数（此前问卷保存被错位嵌入 saveRegisterSettings）

### Fixed
- **v0.5.14 ID 验证重构（UUID 比对优先）+ Economy 崩溃 + 问卷设置**：
  - 验证改为 UUID 比对优先：进服记录与绑定 UUID 不一致时拒绝（防同名冒充）；
    首次验证采用进服记录中的真实 UUID（原 setMinecraftId 伪造随机 UUID 已移除）；
    重复验证幂等返回「已完成验证」（原 400）
  - /api/user/minecraft/status 补 minecraftUuid 字段；verify 响应统一带 data.verified
    （修复 Verify.vue "Cannot read properties of undefined (reading 'verified')"）
  - /api/cmi/player 500（ClassCastException）：快照 JSON 反序列化为 LinkedHashMap
    后显式转换为 PlayerEconomy
  - 问卷设置面板：启用开关 + 通过线（dp_setting questionnaire.config 热生效）；
    问卷未启用时玩家侧显示友好提示卡而非踢回登录

### Changed
- **v0.5.12 README 重写**：面向用户的展示型 README（产品定位/功能总览/三步快速开始/
  迁移能力/FAQ 折叠/路线图），开发者向内容移至 docs/ 文档体系

### Fixed
- **管理员无法进入后台（v0.4.2）**：普通登录接口恒返回 isAdmin=false，前端路由守卫
  依据 localStorage isAdmin 拦截 /admin。修复为语义对齐旧版：dp_setting admins.list
  内的玩家登录时签发 admin 角色令牌并返回 isAdmin=true
- **自定义启动界面（v0.4.1）**：关闭 Spring 原生横幅与启动日志（logback 压制框架日志至
  WARN），启动时打印 DreamPort 字符画 Banner + 中文启动记录清单（数据库/数据表/旧库
  迁移/初始化状态/AI 评分/邮件/邀请/维护模式/运行环境/耗时 + 访问地址）；启动失败输出
  中文原因与排查提示（静态监听器，早于 bean 创建的失败也能捕获）
- **首次启动初始化向导（v0.4.0）**：检测到无用户时 `/setup` 引导创建管理员账号 +
  选择「全新部署」或「上传旧库 .sql 导入」；管理员玩家名与旧数据同名时自动「认领」
  （重置密码/邮箱、设为 approved、写入管理员名单）；完成后向导永久关闭（重复提交 403）
- **管理后台「迁移」标签**：上传旧库 .sql 一键迁移（暂存表隔离，失败不污染现网，
  dp_user 非空时幂等拒绝；播种演示账号自动清理后放行）
- **仅支持 MySQL**：移除 H2 依赖与双迁移目录，dump 语句原生执行

### Changed
- 数据源默认直连 MySQL（env/config.yml 可配）；删除 mysql profile 与 seed-demo
  演示播种（由初始化向导取代）；flyway locations 固定 db/migration/mysql
- config.yml 模板：数据库段为非注释 [必改] 项；占位密钥/令牌改为 ASCII 长串（避免
  JWT 弱密钥拒绝与 HTTP 头编码问题），TokenService 启动时检测占位值并告警
- USER_GUIDE 同步重写快速开始/部署/迁移章节
- **config.yml 单文件部署模式（v0.3.0）**：首次启动自动在工作目录生成带中文注释的部署
  配置（数据库/JWT/服务器令牌/SMTP/LLM/迁移路径一处搞定），编辑后重启即生效；
  无需环境变量。优先级：命令行 > 环境变量（保留支持）> config.yml > 内置默认。
  已实测：配置生成/端口覆盖/自定义 JWT 密钥全部生效
- `wl.ws-port` 可配置 WebSocket 端口
- `docs/USER_GUIDE.md` 使用文档：快速开始/生产部署(systemd/Docker/环境变量)/配置两层说明/
  插件三角色安装/旧版迁移与回滚/玩家与管理员功能指南/命令权限/FAQ/安全清单
- `application.yml` 基础设施凭据全面支持环境变量（WL_JWT_SECRET/WL_SERVER_TOKEN/WL_SMTP_*）
- 项目仓库初始化：git 管理（main 分支）、MIT LICENSE、语义化版本规范
- README：重写版架构简介、模块划分、关键设计决策、路线图（P0–P8）
- 版本基线 `0.1.0-dev`；首个可运行脚手架构建通过后打 `v0.1.0` 标签
- `docs/FEATURE_GAP_vs_参考项目.md`：Legacy vs 参考项目 v1.8.0 功能差距报告
- `docs/IMPLEMENTATION_PROGRESS.md`：128 项功能实现进度总表（P0–P8 逐项状态追踪，吸收项落位编号）
- `docs/PROJECT_DOCUMENTATION.md`：完整项目文档（概览/架构/模块/数据架构/API 契约/安全/配置/部署/谱系声明）
- `docs/API_CONTRACT.md`：API 契约冻结稿（对外 /api/** 与旧版逐字兼容 + /internal/v1 服务器端点）
- **P0+P1 完成**：Maven 多模块脚手架（dreamport-common/server/plugin，构建通过并打 `v0.1.0` 标签）
  - server：Spring Boot 3.4 + Java 21 虚拟线程；Flyway V1（dp_user/dp_audit_log/dp_setting/dp_server）；JWT（role claim）；密码双算法（旧 `$SHA$` 恒时验证 + 透明升级 bcrypt，已实测）；IP 限流；CORS；统一响应包装；`/internal/v1/{login-check,heartbeat}`（X-Server-Token 鉴权）；演示账号播种（dev）
  - common：协议 DTO（LoginCheck/Heartbeat/ErrorCode/Protocol 常量）
  - plugin：三角色骨架（primary/secondary/proxy）+ `/xmw status|reload`

### Changed
- 项目定名 **DreamPort**，中文名 **「夏日小镇 · 梦港」**：主品牌为服务器"夏日小镇"，项目名"梦港"（DreamPort 直译），"夏梦"（XiaMeng → XM）为作者署名；宣传语"进入夏日小镇，先入梦港"
- 命名规范确立：模块 `dreamport-common` / `dreamport-server` / `dreamport-plugin`，Java 包根 `cn.xmcraft.dreamport`，新数据库表前缀 `dp_`，插件名 `DreamPort`
- README 路线图并入 参考项目 吸收项（代理端拦截/评分人工复核/delete 命令/bStats/前端组件库与测试/语言切换/CI/config_help）；**Discord OAuth 经用户决策排除**

- **P2–P8 主体完成**（v0.2.0）：
  - V2 迁移：10 张业务表（邀请/通知/待登录/密码重置/申诉/村谱/机器/问卷×3）
  - 迁移器：9 旧表改名备份+列映射导入、users.json/audits.json、config.yml→dp_setting、幂等报告
  - P3：邮件（双语模板+日志模式）、邮箱验证码、算式图形验证码、密码重置、资料/头像、
    MC/基岩 ID 绑定验证（3 分钟窗口）、问卷（题库入库+YAML 导入、LLM 评分 confidence+
    manualReview+熔断、统一 SSE、申诉）、i18n 全覆盖
  - P4：审核+审计+WS 推送(18899)、邀请状态机、通知、村谱/机器（含截图上传）、玩家目录、
    聊天 SSE、维护模式持久化、多服状态聚合、经济快照榜单、文档中心（防路径穿越）、导出
  - P5：插件完整实现（登录校验+缓存+fail_policy、事件上报、经济采集 Vault/Essentials、
    whitelist 指令队列、/xmw 全命令含 delete、bStats 官方库）
  - P6：前端基线导入+语言切换+SPA 托管（WebStaticConfig 路由回退 + /uploads）
  - P7：AstrBot 兼容端点（X-API-Token）、审核状态查询 FIX(legacy)、config_help 双语
  - P8：Gitea Actions 构建/Release 工作流、Dockerfile、systemd 单元
  - 全链路实测：注册→问卷→审核→进服放行→ID 验证→邀请/村谱/机器/聊天/维护 全部通过

- **v0.2.1 真实数据迁移演练通过（8-2 ✅）**：用户提供的生产 xmc 库 dump（19 用户/71 审计/
  8 邀请/4 通知/4 密码重置/1672 进服记录）本地 MySQL 隔离导入 → DreamPort 自动迁移：
  9 张旧表改名备份、1778 行 1.6s 导入、$SHA$ 哈希逐字节保留（password_algo=legacy_sha256）、
  错误密码登录 401（算法验证）、真实用户进服决策 allow、问卷分数/答案/基岩名/门户内容全保留

### Fixed
- Flyway 接入非空旧库需 baseline-on-migrate: true + baseline-version: 0（演练发现，
  否则 V1 建表被基线吞掉导致 dp_user 缺失）
- 限流器初版差一错误（达到上限时仍放行），已修复并实测 429 生效（达到上限时仍放行），已修复并实测 429 生效
- Spring Data JDBC @Query DELETE 不生效 → 改 JdbcTemplate；进服记录清理条件
  由 login_time 改为 expire_time（修复刚保存记录被误删）
- /api/review/status 改读 query 参数（FIX(legacy)：旧版 X-Username 头与前端不匹配）
- 多服在线数聚合去除重复计数；chatCount/activeDays 假数据改为真实快照统计

### 备注
- 旧版项目（v1.0.0）已归档至 `../XMWhitelist-Legacy/`，作为功能对标与数据迁移的参考基线
