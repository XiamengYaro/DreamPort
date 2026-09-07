# Velocity 代理插件

**DreamPort Proxy** 安装在 Velocity 代理端,提供**群组服统一登录拦截**与状态上报。

## 定位:解决什么问题

单服用 Paper 主插件(role=primary)在登录阶段拦截即可。**群组服**场景:

- 玩家先进代理再转子服——如果每个子服各自拦截,体验差且容易漏
- 本插件在 `ServerPreConnect` 阶段(玩家被转发到子服之前)向后端 `login-check`,不通过直接在代理侧断开
- 子服无需再装拦截逻辑(子服的 Paper 插件配 `role: secondary`/`proxy`,纯上报)

## 安装与配置

1. `dreamport-plugin-proxy-*.jar` 放入 Velocity `plugins/`
2. 首启生成 `plugins/dreamport-proxy/config.properties`(**Properties 格式,不是 YAML**)
3. 编辑后重启:

```properties
backend.url=http://127.0.0.1:18898
backend.server-id=proxy
backend.server-token=change-me-server-token   # 与后端 wl.internal.server-token 一致
check.fail-policy=cache                        # cache | allow | deny
check.cache-ttl-seconds=60
enforce-whitelist=true
```

## 行为

| 功能 | 说明 |
|---|---|
| 统一拦截 | `ServerPreConnect`:先异步上报 `login-record`,再同步 `login-check`(该请求**不含 uuid**,与 Paper 端略有差异);拒绝则断开并显示中文文案 |
| fail-policy | 与 Paper 端一致(cache 用旧缓存兜底;allow 放行;deny 拒绝) |
| 心跳 | 固定 60 秒上报(serverId/role=proxy/在线玩家列表);后端服务器列表因此显示代理在线数据 |
| 启动报告 | 打印后端连通、决策缓存 TTL、**已配置后端服数与在线服数**(对每个子服 ping 汇总)、在线玩家 |

## 踢出文案对照

| reasonKey | 显示 |
|---|---|
| `login.pending` | 等待审核 |
| `login.pending_review` | 问卷通过,待管理员审核 |
| `login.invited_pending` | 等待邀请人确认 |
| `login.pending_verify` / `verify.recorded` | 已记录信息,请回网页完成 ID 验证 |
| `login.rejected` | 已拒绝,可重新答题或申诉 |
| `login.banned` / `login.banned_reason` | 你已被封禁 |
| `maintenance.kick` | 维护中 |
| 其他 / `login.not_registered` | "你还未注册白名单,请先在官网注册" + 后端地址 |

## 已知限制

- 踢出文案**硬编码中文**,不走 i18n(多语言需求需等后续版本)
- 心跳间隔固定 60s,不可配置(配置键 `heartbeat-interval` 未读取)
- 类注释提到的 `/vdp` 命令**未实现**(陈旧注释);插件当前无任何命令
- `@Plugin` 版本号 1.0.1 硬编码,与主插件版本(1.1.0)不一致属正常
