# astrbot_plugin_dreamport

DreamPort(夏日小镇·梦港)的 AstrBot 官方对接插件:**QQ 验证绑定** 与 **群服消息互通**。

> 本插件为 DreamPort 项目的原创实现(MIT License),运行时仅调用 AstrBot 公开插件 API,不含任何第三方项目源码。
> 设计文档:[docs/ASTRBOT_PLAN.md](../docs/ASTRBOT_PLAN.md) · 后端 v1.1 契约:[docs/API_CONTRACT.md](../docs/API_CONTRACT.md)

## 功能

| 功能 | 说明 |
|------|------|
| QQ 验证绑定 | `/dp 绑定` → 后端生成 6 位一次性验证码(5 分钟)→ **私聊**送达 → 玩家在网页「个人中心 → QQ 绑定」或游戏内 `/xmw qq bind <码>` 确认 |
| 群→服 | 绑定群消息经后端校验(群白名单/all/prefix 模式)后进游戏与网页聊天室;指令与机器人自身消息不转发(防回环) |
| 服→群 | 游戏聊天/进出服/网页聊天按模板渲染后实时发群(SSE 长连,断线指数退避重连) |
| 查询 | `/dp 状态`、`/dp 玩家`、`/dp 查询` |

## 安装

1. 将本目录(`astrbot_plugin_dreamport` 的内容)放入 AstrBot 的 `data/plugins/astrbot_plugin_dreamport/`(或通过 AstrBot WebUI「插件」页从本地安装),重启 AstrBot,依赖 `aiohttp` 会按 `requirements.txt` 自动安装。
2. 确认 QQ 协议端(NapCat 等)已通过 OneBot v11 反向 WS 接入 AstrBot(aiocqhttp 适配器,默认端口 6199)。

## 配置

在 AstrBot WebUI「插件 → astrbot_plugin_dreamport → 配置」中填写:

| 配置项 | 说明 |
|--------|------|
| `backend_url` | DreamPort 后端地址,如 `http://10.0.0.6:18898` |
| `api_token` | API 令牌——DreamPort 管理后台「系统设置 → QQ 互通」开启集成并生成 |
| `platform_id` | AstrBot 消息平台适配器 ID(主动发消息时构造 umo 用,WebUI 消息平台页可见) |
| `forward_groups` | 参与互通的 QQ 群号,逗号分隔;**须与后端「QQ 互通 → 群绑定」一致** |

## 后端侧配置(一次性)

1. 管理后台 → 系统设置 → **QQ 互通**:启用集成、生成 API 令牌、填写群绑定(群号 / mode: all 或 prefix / 进退服转发开关)、按需调整消息模板——全部热生效。
2. 后端关闭集成(`astrbot.enabled=false`)时,本插件所有后端调用返回 403。

## 真机联调清单

后端与 AstrBot 部署联通后,按序核对:

- [ ] `/dp 状态` 返回在线人数(后端 token 正确)
- [ ] `/dp 绑定` 在群内触发 → 机器人**私聊**送达验证码(平台 ID 配置正确)
- [ ] 验证码在网页个人中心输入 → 绑定成功 → `/dp 查询` 可见
- [ ] 游戏内 `/xmw qq bind <码>` 同样可完成绑定
- [ ] 群内普通消息 → 游戏内出现 `[QQ] 昵称: 消息`(约 ≤1s)
- [ ] 游戏内聊天 → 群内出现 `[服务器名] 玩家: 消息`(随心跳/事件实时)
- [ ] 玩家进服/退服 → 群内出现进退服提示(未勾选 `forward_join_quit` 的群除外)
- [ ] 网页聊天室发言 → 群内可见(网页消息同时进游戏)
- [ ] 机器人回复(如 LLM)不会回流到游戏/群(防回环)
- [ ] prefix 群:不带前缀消息不进服,带前缀才进服
- [ ] 重启 AstrBot → SSE 自动重连,断线期间消息经轮询/队列不堆积报错

## 限制说明

- 验证码与互通队列为后端内存态,后端重启后未使用的验证码失效(重新申请即可),互通消息不回放。
- 个人号协议端(NapCat 等)受腾讯风控影响,建议消息模板克制、控制转发量;后续可扩展 QQ 官方机器人 API 适配。
