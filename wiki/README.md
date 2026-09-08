# DreamPort 文档库

> **DreamPort · 夏日小镇 · 梦港** —— Minecraft 服务器门户与玩家管理系统。
> 本文档库覆盖 DreamPort 全家桶:后端服务、Paper 主插件、Velocity 代理插件、AstrBot QQ 插件。

当前版本:**后端与 Paper 插件 1.2.0** · Velocity 插件 1.2.0 · AstrBot 插件 1.0.1

---

## 组件总览

| 组件 | 运行位置 | 技术 | 作用 |
|---|---|---|---|
| [DreamPort 后端](admin/backend/introduction.md) | 独立服务器(Java 21) | Spring Boot 3.4 + MySQL | 门户网站、REST API、WebSocket、管理后台、全部业务逻辑 |
| [DreamPort 主插件](admin/plugin-paper/introduction.md) | MC 服务器(Paper 1.20+) | Java(Paper API) | 进服白名单校验、事件上报、经济快照、聊天互通、游戏内命令 |
| [DreamPort Proxy](admin/plugin-velocity/introduction.md) | Velocity 代理端 | Java(Velocity API) | 群组服统一登录拦截、状态上报 |
| [AstrBot 插件](admin/plugin-astrbot/introduction.md) | AstrBot(Python) | Python(aiohttp) | QQ 验证绑定、群服消息互通、QQ 查询指令 |

## 文档导航

### 🎮 面向玩家

| 文档 | 内容 |
|---|---|
| [快速上手](player/getting-started.md) | 三条进服路径,看看你属于哪一种 |
| [注册 · 问卷 · 审核](player/register-and-review.md) | 注册账号、答题、审核状态与申诉 |
| [ID 验证](player/verify-identity.md) | Java/基岩 ID 验证怎么做 |
| [QQ 与聊天](player/qq-and-chat.md) | QQ 绑定、群服聊天互通、网页聊天室 |
| [玩家常见问题](player/faq.md) | 被踢出提示对照、验证码、积分等 |

### 🛠 面向服主

**部署总览**:[组件拓扑 · 端口 · 令牌](admin/deployment-overview.md)

**后端(dreamport-server)**

| 文档 | 内容 |
|---|---|
| [架构与技术栈](admin/backend/introduction.md) | 模块、鉴权体系、端口 |
| [部署与初始化](admin/backend/installation.md) | JAR 部署、config.yml、/setup 向导、systemd、升级备份 |
| [配置详解](admin/backend/configuration.md) | config.yml / application.yml 全键 |
| [管理后台](admin/backend/admin-panel.md) | 13 个功能页逐页说明 |
| [API 参考](admin/backend/api-reference.md) | REST API 分组与契约 |
| [数据库与定时任务](admin/backend/database-and-tasks.md) | 数据表、Flyway、7 个定时任务、邮件模板 |
| [头像渲染服务](admin/backend/avatar-service.md) | 双层皮肤大头照、皮肤来源分流、两级缓存 |

**插件**

| 插件 | 文档 |
|---|---|
| Paper 主插件 | [简介与角色](admin/plugin-paper/introduction.md) · [安装与配置](admin/plugin-paper/installation.md) · [进服校验与维护模式](admin/plugin-paper/whitelist-and-maintenance.md) · [聊天与事件互通](admin/plugin-paper/chat-and-events.md) · [经济快照](admin/plugin-paper/economy.md) · [命令与 i18n](admin/plugin-paper/commands.md) |
| Velocity 代理 | [简介/安装/配置/行为](admin/plugin-velocity/introduction.md) |
| AstrBot QQ | [简介与安装](admin/plugin-astrbot/introduction.md) · [配置与对接](admin/plugin-astrbot/configuration.md) · [指令与群服桥](admin/plugin-astrbot/commands.md) |

**[全组件故障排查速查](admin/troubleshooting.md)**

---

## 相关仓库文档

- [docs/USER_GUIDE.md](../docs/USER_GUIDE.md) — 门户功能使用手册
- [docs/OPERATIONS_CHECKLIST.md](../docs/OPERATIONS_CHECKLIST.md) — 运维检查清单
- [docs/API_CONTRACT.md](../docs/API_CONTRACT.md) — 后端 API 完整契约
- [docs/ASTRBOT_PLAN.md](../docs/ASTRBOT_PLAN.md) / [docs/CHAT_SERVERINFO_PLAN.md](../docs/CHAT_SERVERINFO_PLAN.md) — 设计文档

## 许可与来源

- 全部代码原创实现(MIT),禁止引入第三方 GPL 代码(见仓库 [Rules.md](../Rules.md))
