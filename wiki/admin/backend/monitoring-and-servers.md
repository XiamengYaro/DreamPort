# 监控与多服令牌 · 资源看板 · 地图在线玩家

后台「数据统计」资源看板 + 「服务器管理」Tab + 地图页在线位置侧栏的使用说明。三者数据源均为**插件心跳**（60s 周期，Paper 插件 `features.report-metrics: true` 默认开启）。

## 服务器资源监控

- 插件每次心跳附带：TPS（1/5/15 分钟）、平均 tick 耗时、JVM 内存、进程 CPU 占用、运行时长；Velocity 代理无 tick 循环，仅报内存/CPU
- 后端每条心跳落 `dp_server_metrics` 表，**保留 7 天**（采样任务顺带清理）
- 「数据统计」Tab 底部「服务器资源监控」面板：分服选择 + TPS/内存/CPU 实时卡 + 24h/7 天趋势曲线
- 玩家侧「控制台 → 服务器状态」TPS 卡显示主服真实 TPS（旧版为固定 20.0；无指标数据显示「—」）

### TPS 低阈值告警

- 默认阈值 **15.0**，某服连续 3 次心跳低于阈值即触发：管理员铃铛 + 通知邮箱（`管理员通知邮箱` 配置）
- 每服 1 小时冷却，避免告警轰炸；阈值/开关存 `dp_setting(metrics.config)`：`{"tpsAlertEnabled": true, "tpsThreshold": 15.0}`

## 按服令牌（per_server）

默认 `shared` 模式与旧版完全一致：所有游戏服在 `config.yml → backend.server-token` 填同一个后端 `wl.internal.server-token`。

需要**按服吊销**能力时切到 `per_server`（服务器管理 Tab → 鉴权模式）：

1. 在「服务器管理」Tab 对每个 serverId 点「签发令牌」——明文**仅显示一次**，立即粘贴到对应游戏服的 `config.yml → backend.server-token` 并重启该服插件
2. 后端只存 SHA-256 哈希（`dp_server.token_hash`）；轮换 = 重新签发，旧令牌即时失效
3. per_server 模式下 `/internal/v1/**` 请求按 `X-Server-Id` 头匹配该服令牌，且「停用」的服务器所有请求被拒
4. **应急通道**：后端全局 `wl.internal.server-token` 在 per_server 模式下仍然有效（命中会记录 warn 日志），用于紧急恢复

> 提示：旧插件不发送任何新字段、不做任何改变，升级后端无需同步升插件；要出指标数据才需要更新 Paper 插件。

## 地图在线玩家侧栏

- 「门户管理 → 基础信息 → 地图」支持多条目（名称 / 地址 / 类型）：`BlueMap`、`Dynmap`、`通用网页`
- 类型决定地图页行为：BlueMap/Dynmap 会显示**「在线位置」侧栏**（玩家头像+世界+坐标，15 秒刷新，点击玩家跳转地图定位），通用网页仅嵌入
- 数据由后端代理：`GET /api/map/live?i=<地图下标>`，请求**不能**指定任意 URL（只允许后台已配置条目，杜绝 SSRF）；拉取失败时侧栏显示空态，地图 iframe 不受影响
- 旧版 `map_url`（竖线分隔多地址）自动兼容为「通用网页」条目，重新编辑保存后即可补名称与类型
