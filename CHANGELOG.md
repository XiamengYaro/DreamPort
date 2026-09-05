# Changelog

本项目的所有显著变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本管理遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- 项目仓库初始化：git 管理（main 分支）、MIT LICENSE、语义化版本规范
- README：重写版架构简介、模块划分、关键设计决策、路线图（P0–P8）
- 版本基线 `0.1.0-dev`；首个可运行脚手架构建通过后打 `v0.1.0` 标签

### Changed
- 项目定名 **DreamPort · 夏梦之门**（XM DreamPort）：夏梦（XiaMeng → XM）+ Port（Portal 门户 / Harbor 港湾 双关）
- 命名规范确立：模块 `dreamport-common` / `dreamport-server` / `dreamport-plugin`，Java 包根 `cn.xmcraft.dreamport`，新数据库表前缀 `dp_`，插件名 `DreamPort`

### 备注
- 旧版项目（v1.0.0）已归档至 `../XMWhitelist-Legacy/`，作为功能对标与数据迁移的参考基线
