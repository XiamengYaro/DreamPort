# AstrBot 插件 · 配置与对接

## 插件配置(AstrBot WebUI → 插件配置)

| 键 | 默认 | 说明 |
|---|---|---|
| `backend_url` | `http://127.0.0.1:18898` | DreamPort 后端地址(自动去尾斜杠) |
| `api_token` | 空 | **必填**——后端管理后台「QQ 互通」生成的令牌;为空将无法通过鉴权(自检日志会打 `!!`) |
| `platform_id` | `default` | AstrBot 平台适配器 ID,用于构造主动发消息的 umo(私聊投递验证码等);与你的 aiocqhttp 适配器 ID 一致 |
| `forward_groups` | 空 | 参与互通的 QQ 群号,**逗号分隔**;为空则不转发任何群消息 |

配置**热生效**(每次请求现读),改完无需重启。所有配置都是字符串类型。

## 后端侧一次性配置

管理后台 → 系统设置 → **QQ 互通(AstrBot)**:

| 项 | 说明 |
|---|---|
| 启用 | 总开关;关闭时插件所有请求 403 |
| API 令牌 | 「生成」按钮,复制到插件 `api_token` |
| 群绑定 | JSON 数组,与 `forward_groups` 保持同群号: `[{"group": 123456, "mode": "all", "prefix": "#", "forward_join_quit": true}]` |
| 消息模板 | 进群/退服/聊天渲染格式 |

`mode` 语义:`all`=该群全部消息转发进服;`prefix`=仅 `#` 开头消息进服(群公告建议注明)。`forward_join_quit`=进退服是否发群。

## 对接自检清单

1. `/dp 状态` 有响应(令牌+地址正确)
2. 群内 `/dp 绑定` → 验证码**私聊送达**(platform_id 正确的标志)
3. 网页「QQ 绑定」或游戏内 `/xmw qq bind <码>` 确认成功
4. 群→服:群里说话,游戏内出现 `[QQ] 昵称: 消息`(约 ≤1 秒)
5. 服→群:游戏内说话,群内出现 `[服务器名] 玩家: 消息`
6. 进退服提示出现(`forward_join_quit` 未勾的群除外)
7. 网页聊天室消息进群
8. 机器人自己的消息**不会**回流游戏(防回环)
9. `prefix` 群仅 `#` 消息进服
10. 重启 AstrBot → SSE 自动重连(日志:`DreamPort 下行 SSE 已连接`)
11. 指令(`/dp` 开头)不会转发进服

## 消息流转细节

- **上行**:命中 `forward_groups` 的群文本 → `POST /api/astrbot/chat {group, sender_id, sender_name, message}`;以 `/` 开头的指令与机器人自身消息被过滤;发送失败仅 debug 日志,不打扰群聊
- **下行**:SSE 事件 `{"group", "text"}` → 发往对应群;目标会话优先用真实缓存 umo,否则构造 `{platform_id}:GroupMessage:{群号}`;发送失败 warning 日志
- **SSE 惰性启动**:首次群消息成功上行后才建立连接(要求 api_token 非空);断线重试 3s 起指数退避,封顶 60s,成功后归位
