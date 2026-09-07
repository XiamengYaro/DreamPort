# Paper 主插件 · 简介与角色

**DreamPort 主插件**安装在每一台 MC 服务器(Paper 1.20+,兼容 Folia)上,是后端在游戏内的"手和脚"。

## 核心能力

- **进服校验**:登录时向后端实时核验白名单/封禁/维护模式
- **事件上报**:进服/退服/聊天事件推给后端(驱动 QQ 桥、聊天落库)
- **消息广播**:把网页/QQ 消息广播到游戏内(收件箱轮询)
- **经济快照**:定期汇总玩家余额/时长上报(Vault 或 EssentialsX 数据)
- **白名单指令队列**:管理后台/网站操作转换为服务器控制台命令执行
- **游戏内命令**:`/xmw` 系列(审核/封禁/QQ 绑定等)
- **心跳**:服务器在线状态与在线列表(官网状态页、离线告警)

## 三种角色(role)

| role | 定位 | 行为 |
|---|---|---|
| `primary`(默认) | 主服 | 进服拦截 + 事件上报 + 经济上报(auto 模式下) |
| `secondary` | 群组子服 | **仅状态上报**,不做进服拦截(拦截交给代理) |
| `proxy` | 代理背后的 Paper 端 | 同 secondary,上报模式;拦截由 Velocity 插件统一执行 |

> 单服部署用 `primary`;群组服:Velocity 装 [代理插件](../plugin-velocity/introduction.md) 且子服全部用 `secondary`/`proxy`,经济由主服上报(`economy.report: auto`)。

## 工作原理

```
玩家登录 → LoginListener(HIGH):
  1. 无条件异步上报 login-record(供网页 ID 验证比对)
  2. 同步调 login-check(缓存优先,认证线程不卡主线程)
  3. 后端返回 decision:
     allow → 放行
     deny  → 按 reasonKey 显示中文踢出文案
     maintenance && !op → 踢出(维护中)

进服后:
  PlayerEventsListener:join/quit/chat → 上报后端
  ScheduledTasks:心跳/收件箱轮询/白名单指令队列/经济快照
```

## 与后端的通道

全部 HTTP(`/internal/v1/**`),请求头 `X-Server-Id` + `X-Server-Token`,值取自插件配置。断线时按 `check.fail-policy` 兜底(见[进服校验](whitelist-and-maintenance.md))。

## 兼容性

- Paper API 1.20(`api-version: '1.20'`),声明 Folia 支持(调度用 AsyncScheduler/GlobalRegionScheduler)
- 经济读取:Vault(反射,无硬依赖)优先,EssentialsX userdata 回退
- bStats 匿名统计(可关)
