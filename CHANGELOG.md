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

### Fixed
- 限流器初版差一错误（达到上限时仍放行），已修复并实测 429 生效

### 备注
- 旧版项目（v1.0.0）已归档至 `../XMWhitelist-Legacy/`，作为功能对标与数据迁移的参考基线
