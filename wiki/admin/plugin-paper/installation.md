# Paper 主插件 · 安装与配置

## 安装

1. 将 `dreamport-plugin-*.jar` 放入服务器 `plugins/` 目录
2. 启动一次生成 `plugins/DreamPort/config.yml`
3. 编辑配置(见下)→ 重启或 `/xmw reload`

## config.yml 全键

```yaml
language: zh                    # zh | en
role: primary                   # primary | secondary | proxy
bstats:
  enabled: true                 # 匿名统计
backend:
  url: "http://127.0.0.1:18898" # 后端地址(自动去尾斜杠)
  server-id: "main"             # 服务器标识(多服各不相同;后端按它区分)
  server-token: "dev-internal-token"  # 必须与后端 wl.internal.server-token 一致
check:
  fail-policy: cache            # 后端不可达兜底: cache | allow | deny
  cache-ttl-seconds: 60         # 校验结果缓存时长
  timeout-ms: 1500              # 单次请求超时
features:
  enforce-whitelist: true       # 进服拦截开关(primary 有效)
  forward-chat: true            # 游戏聊天上报(QQ/网页可见)
  report-join-quit: true        # 进退服上报(群播报/官网展示)
  receive-chat: true            # 接收网页/QQ 消息并游戏内广播
  message-poll-seconds: 2       # 收件箱轮询间隔(≥1)
  report-metrics: true          # 心跳附带 TPS/内存/CPU 指标(后台资源看板数据源)
economy:
  report: auto                  # auto=仅主服上报 | on=始终 | off
web-register-url: "http://localhost:18898"  # 被踢提示中的注册地址
tasks:
  heartbeat-interval: 60        # 心跳间隔(≥10)
  whitelist-poll-interval: 30   # 白名单指令队列轮询(≥5)
  economy-interval: 300         # 经济快照间隔(≥30)
```

### 模板默认值 vs 代码硬默认

个别键**删掉后**代码默认与模板不同,排查时注意:

| 键 | 模板 | 代码缺省 |
|---|---|---|
| `backend.server-token` | `dev-internal-token` | **空串**(鉴权必失败,务必显式配置) |
| `features.forward-chat` / `report-join-quit` | true | **false** |

下限钳制:`message-poll-seconds ≥1`、`heartbeat-interval ≥10`、`whitelist-poll-interval ≥5`、`economy-interval ≥30`。

## 后端侧对应配置

| 插件键 | 后端 |
|---|---|
| `backend.server-token` | `config.yml` → `wl.internal.server-token`(或环境变量 `WL_SERVER_TOKEN`) |
| `backend.server-id` | 后端服务器列表/状态页按此区分;`primary` 建议命名 `main` |
| `backend.url` | 后端 HTTP 地址(18898),不要带尾斜杠 |

## 群组服部署建议

- Velocity 装[代理插件](../plugin-velocity/introduction.md)统一拦截
- 每个子服:`role: secondary`(或 `proxy`),`server-id` 各不相同(如 `survival`/`creative`)
- 经济:只让主服上报(`economy.report: auto` + 主服 role=primary),避免快照互相覆盖

## 多语言

`language: zh|en`;踢出文案打包内置(12 个键),en 缺失键回退中文。自定义文案可用资源包替换或后续版本外置。

## 验证安装

```
/xmw status
# 角色: primary · 后端: http://127.0.0.1:18898 · 服务器 ID: main · fail-policy: cache
# 后端连通: {"status":"ok",...}
```

后端日志同时出现 `[连接] xxx 服务器上线` 即对接成功。
