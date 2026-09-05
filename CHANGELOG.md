# Changelog

本项目的所有显著变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本管理遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
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

### Fixed
- 限流器初版差一错误（达到上限时仍放行），已修复并实测 429 生效
- Spring Data JDBC @Query DELETE 不生效 → 改 JdbcTemplate；进服记录清理条件
  由 login_time 改为 expire_time（修复刚保存记录被误删）
- /api/review/status 改读 query 参数（FIX(legacy)：旧版 X-Username 头与前端不匹配）
- 多服在线数聚合去除重复计数；chatCount/activeDays 假数据改为真实快照统计

### 备注
- 旧版项目（v1.0.0）已归档至 `../XMWhitelist-Legacy/`，作为功能对标与数据迁移的参考基线
