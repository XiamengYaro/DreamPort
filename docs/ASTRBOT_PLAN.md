# AstrBot 重构方案 —— QQ 验证绑定 + 群服消息互通

> 状态:**已定稿**(v1.1,2026-09-06;§9 决策点已由作者确认)
> 分支:`dev` · 适用版本:v1.0.1 之后的新功能开发线
> 结论先行:现有 AstrBot 集成**不可用**(详见 §1),本方案将其重构为「AstrBot 插件 ⇄ 后端 REST/SSE」的双向架构。

---

## 1. 现状验证:为什么跑不通

对运行中的后端(v1.0.1,本机 :18898,测试库 dp_setup_test)做了实测 + 代码全量走查:

| # | 问题 | 证据 | 影响 |
|---|------|------|------|
| 1 | **`astrbot.api_token` 无处设置** | config.yml 模板/application.yml/config_help 均无该键;dp_setting 表中不存在;唯一入口是 `POST /api/admin/system-config`(需管理员 JWT) | 开箱即全部端点 401,且无文档指引。实测:`GET /api/astrbot/status` → 401 |
| 2 | **lookup 两个端点结构性不可调用** | `AstrBotController` 写了 `@GetMapping("/lookup/qq/{qq}")` 却用 `@RequestParam String qq` 收参。实测:路径风格 `/lookup/qq/12345` → **400**(参数解析失败,先于方法体);查询风格 `/lookup/qq?qq=12345` → **404**(路由要求第三段路径)。**不存在任何能调通的方式** | 机器人查询绑定关系必失败 |
| 3 | **消息进服下行链路整体缺失** | `/api/astrbot/chat` 与网页 `/api/chat/send` 都只进 `ChatService.broadcast`(网页 SSE + dp_setting 历史);插件只有 30s 白名单指令轮询,没有消息拉取端点,也不连 18899 WS。后端→游戏的聊天通路不存在 | QQ→游戏、网页→游戏两个方向全是断头路(旧版同进程 `Bukkit.broadcastMessage` 的能力在架构拆分时丢失) |
| 4 | **绑定无任何验证** | `POST /api/astrbot/bind {qq, minecraftName}` 拿到 token 即可直接写库,不校验用户状态(banned 也可绑)、无验证码/确认环节 | "QQ 验证"名不副实,冒绑风险 |
| 5 | **字段名契约漂移** | bind 收 `qq`(调用方按旧版发 `qqNumber`);status 返回 `max`(旧 `maxPlayers`);players 返回 `servers`(旧 `players[]+count`);lookup 返回 `username/qq`(旧 `minecraftName/qqNumber`) | 按任何已有文档/习惯写的调用端都解析不到字段 |
| 6 | **无开关、无群配置** | 旧版就有 `astrbot.enabled` / `group_id` 键(存而不用),DreamPort 连键都没有;不支持配置哪些群参与互通 | 无法关闭/圈定功能范围 |
| 7 | 玩家数据滞后 | status/players 读 60s 心跳内存快照 | 机器人看到的人数最多延迟 1 分钟(可接受,记录在案) |

另:本仓库(及 Legacy `astrbot/` 目录)从未有过自研 QQ 侧代码;Legacy 实际用的是第三方 `astrbot_plugin_minecraft_adapter` + `AstrBotAdapter`(Railgun19457),其绑定数据存插件自己的 bindings.json,与白名单库完全脱节。

**判定:现有 AstrBot 端点中,只有 status/players(设 token 后)和 bind/unbind(不安全)半可用;lookup 必挂;消息互通完全没有。重构而非修补。**

---

## 2. 目标与范围

**目标**
1. **QQ 验证绑定**:QQ 与 DreamPort 账号(即 MC 身份)的强绑定——QQ 侧发起、一次性验证码、网页确认,替代现在的免验证直绑。
2. **群服消息互通**:双向实时——QQ 群消息进游戏(经后端),游戏聊天/进出服进 QQ 群;网页聊天同步纳入同一通路。

**非目标(本期不做)**
- LLM 群聊对话(AstrBot 自身能力,与本项目无关)
- QQ 官方机器人 API 通道(个人号协议端已覆盖;官方 API 限群场景受限,留作适配器扩展点)
- 游戏内 QQ 绑定指令(`/xmw qq <码>`)——网页账号已通过进服验证与 MC 身份强关联,网页确认即等价绑定;如需要后补

---

## 3. 开源调研摘要(实现方式依据)

| 项目 | 架构 | 借鉴点 |
|------|------|--------|
| [AstrBot](https://github.com/AstrBotDevs/AstrBot)(40k★,活跃,AGPL-3.0) | Python 机器人框架;QQ 经 aiocqhttp 适配器(OneBot v11,NapCat 反向 WS 接入 :6199) | 插件模型:`@filter.event_message_type(GROUP_MESSAGE)` 监听群消息,`event.get_group_id()/get_sender_id()/message_str` 取数据,`context.send_message(umo, chain)` 主动发消息,`_conf_schema.json` 自动生成配置 UI |
| [astrbot_plugin_minecraft_adapter](https://github.com/Railgun19457/astrbot_plugin_minecraft_adapter) + [AstrBotAdapter](https://github.com/railgun19457/AstrBotAdapter)(Legacy 同款,活跃) | MC 侧插件开 WS+REST(:8765),AstrBot 插件作客户端接入;Token+心跳+占位符模板 | WS/Token/心跳参数、双向消息模板占位符、转发前缀防刷屏;但其绑定是轻量直绑(存插件自己的文件),与业务库脱节——正是要避开的设计 |
| [MiraiMC](https://github.com/DreamVoid/MiraiMC) | mirai 协议端内嵌 MC 进程 | 反面参考:重依赖、作者声明可能停更,生态已迁 OneBot v11 |
| [Coloryr/Minecraft_QQ](https://github.com/Coloryr/Minecraft_QQ) | MC 插件 ⇄ 外置 bot TCP 长连 | 对话模式三档(不转发/前缀转发/全部转发)、玩家指令不进群 |
| [OneBot v11 标准](https://github.com/botuniverse/onebot-11) | 协议规范 | 群消息事件结构、`send_group_msg`、防回环靠 `self_id` 过滤 |

**业界共性**:传输层现代主流是 WebSocket/HTTP 长连 + Token;消息格式走双向模板;绑定普遍轻量直绑,**没有项目做一次性验证码强验证**——本方案的验证码流程是差异化设计,各组件(AstrBot 插件 API + 后端 + 前端)均能支撑。

---

## 4. 总体架构

```
                    ┌────────────────────────────┐
  QQ 群/私聊 ◀──────▶ │ astrbot_plugin_dreamport   │  ← 自研 AstrBot Python 插件(新建)
   (NapCat/OneBot)   │ 指令·群消息·SSE 收QQ下行     │
                    └──────┬──────────▲──────────┘
                    REST(上行)   SSE(下行,token=查询参数)
                           ▼        │
                    ┌────────────────────────────┐
                    │ DreamPort 后端 :18898       │
                    │ /api/astrbot/**(X-API-Token)│
                    │ QqBridgeService(新):        │
                    │  绑定码状态机 / 出站队列 /    │
                    │  游戏收件箱 / 群绑定校验      │
                    └──────┬──────────▲──────────┘
                    轮询(下行进服,2s)  事件上报(上行,已有)
                           ▼        │
                    ┌────────────────────────────┐
                    │ dreamport-plugin (Paper)    │
                    │ 新增:收件箱轮询→游戏内广播    │
                    └────────────────────────────┘
```

**职责划分**(对照 AstrBotAdapter 的教训,业务数据全部收敛到后端):
- **AstrBot 插件**:只做 QQ 侧「收发与透传」——处理 QQ 指令、把群消息 POST 给后端、通过 SSE 收后端出站消息发到对应群。不存任何业务状态。
- **后端**:绑定码状态机、群绑定配置、消息模板渲染、双向队列与扇出、鉴权与频控。唯一数据权威。
- **Paper 插件**:只加一个「游戏内收件箱轮询 → `Bukkit.broadcastMessage`」,复用现有轮询模式(与 whitelist 指令队列同构)。

**备选方案对比**(详见调研报告):
- B. Paper 插件直开 WS 让 AstrBot 接(AstrBotAdapter 同款):互通绕过后端,绑定与白名单体系割裂,群组服下代理通道受限 → 弃。
- C. Spring Boot 直连 NapCat(OneBot v11):全 Java 但放弃 AstrBot 生态(LLM/多平台/WebUI),用户已选定 AstrBot → 弃。
- D. mirai 内嵌(MiraiMC 式):维护风险高、AGPL 传染顾虑 → 弃。

---

## 5. 详细设计

### 5.1 配置(dp_setting,热生效,管理后台「QQ 互通」卡片管理)

| 键 | 类型 | 默认 | 说明 |
|----|------|------|------|
| `astrbot.enabled` | bool | `false` | 总开关;关闭时 /api/astrbot/** 全部 403(除返回提示) |
| `astrbot.api_token` | string | 空 | 已有键,保留;管理后台加「生成随机令牌」按钮;为空 = 功能未启用 |
| `astrbot.group_bindings` | JSON | `[]` | `[{"group":123456,"mode":"all","prefix":"#","forward_join_quit":true}]`;mode ∈ `all`(全部转发)| `prefix`(仅带前缀的消息进服),**默认 all** |
| `astrbot.forward.game_to_qq` | bool | `true` | 游戏聊天/进出服 → 群 |
| `astrbot.forward.web_to_qq` | bool | `true` | 网页聊天 → 群 |
| `astrbot.forward.qq_to_game` | bool | `true` | 群消息 → 游戏内 |
| `astrbot.template.qq_chat` | string | `[{server}] {player}: {message}` | 服→群模板 |
| `astrbot.template.qq_join` / `qq_quit` | string | `{player} 加入了服务器` / `{player} 离开了服务器` | |
| `astrbot.template.game_chat` | string | `[QQ] {sender}: {message}` | 群→服模板(后端渲染后广播进游戏) |
| `astrbot.template.web_chat` | string | `[网页] {player}: {message}` | 网页→服模板(渲染后进游戏收件箱) |

所有跨端文本由**后端统一模板渲染**,两端插件不做格式拼装(便于双语与管理)。config.yml 模板与 config_help 增加对应说明,消除现状问题 #1/#6。

### 5.2 QQ 验证绑定(核心流程)

```
[QQ] 用户在群/私聊发送: /dp绑定
  → 插件 POST /api/astrbot/bind/request {qq}
  → 后端: enabled 检查 → 频控(每 QQ 1 次/分钟、5 次/天)→ 生成 6 位数字码
      内存存储 {code→{qq, expiresAt=+5min, failCount}},不做持久化(重启失效可接受)
  → 响应 {code, expires_in: 300}
[插件] 私聊发送验证码给用户(群内只回「验证码已私聊发送,5 分钟内有效」)
[网页] 玩家登录 → 个人中心「QQ 绑定」卡片输入验证码
  → POST /api/user/qq/bind {code}(Bearer JWT)
  → 后端: 码匹配且未过期 → 单绑定语义(清除该 QQ 在其他账号的旧绑定)
      → 写 dp_user.qq_number / qq_bound_at → 返回脱敏 QQ
[游戏·备选通道] 玩家游戏内执行 /xmw qq bind <验证码>
  → 插件 POST /internal/v1/qq/bind {player, code}(X-Server-Token)
  → 后端: 按 player 找账号 → 码匹配 → 同上绑定(已进服验证过 MC 身份,等价强绑定)
[解绑] 网页一键解绑 POST /api/user/qq/unbind;或 QQ 端 /dp解绑 → POST /api/astrbot/unbind {qq}
[查询] QQ 端 /dp查询 → GET /api/astrbot/lookup/qq/{qq}(修复后)
```

- 校验规则:码错误 3 次作废;同一 QQ 存在未过期码时重新请求覆盖旧码;被 ban 账号**允许**绑定(绑定≠白名单,审核状态独立)。
- 安全本质:绑定同时证明「控制该 QQ 号」(收码)+「控制该网页账号」(登录态),两端各自已有一层身份,合成强绑定。

### 5.3 群服消息互通

**上行(服→群)**:游戏聊天/进出服现有链路不变(`PlayerEventsListener` → `/internal/v1/events` → `ChatService.broadcast`),在 `ChatService.broadcast` 增加 origin 标记(`game`/`web`/`qq`/`system`),新增 `QqBridgeService.onBroadcast(...)`:
- 按 `astrbot.forward.*` 开关与 group_bindings 过滤 → 模板渲染 → 追加**出站队列**(内存,`AtomicLong seq` 递增,保留最近 200 条)。

**下行(QQ→服)**:插件监听 `forward_groups` 内的群消息 → 过滤(自己 `self_id` 的消息、以 `/` 开头的指令)→ 按群 mode(prefix/all)决定是否转发 → `POST /api/astrbot/chat {group, sender_id, sender_name, message}` → 后端校验群白名单 → `game_chat` 模板渲染 → `ChatService.broadcast(origin=qq)` → 网页 SSE + 历史 + **游戏收件箱**。

**游戏下行(网页/QQ → 游戏内,新增)**:`ChatService`/`QqBridgeService` 维护**游戏收件箱**(内存 seq 队列);Paper 插件新增轮询:

- `GET /internal/v1/messages/pending?since={seq}`(X-Server-Token,响应 `{messages:[{seq, text}]}`)
- 轮询间隔 `features.message-poll-seconds`(默认 2s,可配),`features.receive-chat`(默认 true)开关
- 插件收到 → `Bukkit.broadcastMessage(text)`(已由后端格式化)

**AstrBot 插件下行通道**:`GET /api/astrbot/stream?token=`(SSE,事件 `{seq, group, text}`;复用 v0.5.22 的 `X-Accel-Buffering: no` 反代方案);插件断线自动重连。备选 `GET /api/astrbot/messages?since={seq}` 轮询端点同时提供,作为 SSE 不可用时的 fallback。

**防回环三重保障**:①插件丢弃 `sender_id == bot self_id`;②消息带 origin,`origin=qq` 的不进 QQ 出站队列;③指令消息不转发。群→服默认 `all` 全部转发(作者确认),可在群绑定中改 `prefix` 模式防刷屏。

### 5.4 API 契约重定义(/api/astrbot/**,v1.1)

鉴权:`X-API-Token`(保留)+ `astrbot.enabled` 门禁。**旧免验证 `POST /bind` 移除**(破坏性变更,记 CHANGELOG;消费方只有自研插件,无兼容负担;`/api/astrbot/**` 不在前端 api.ts 中,前端零影响)。

| 端点 | 方法 | 请求 → 响应 | 变化 |
|------|------|------------|------|
| `/api/astrbot/status` | GET | → `{online, max, servers, version}` | 保留(字段名就此定版) |
| `/api/astrbot/players` | GET | → `{count, players:[{name, server}], servers:[...]}` | 增加 count/players 扁平列表,便于机器人 |
| `/api/astrbot/bind/request` | POST | `{qq}` → `{code, expires_in}` | **新增**(验证码申请) |
| `/api/astrbot/unbind` | POST | `{qq}` → 解绑 | 重定义(按 QQ 解绑) |
| `/api/astrbot/lookup/qq/{qq}` | GET | → `{found, username, status}` | **修复**(真路径参数) |
| `/api/astrbot/lookup/mc/{mc}` | GET | → `{found, qq, bound}` | **修复**;`bound` 表示该账号是否已绑 QQ |
| `/api/astrbot/chat` | POST | `{group, sender_id, sender_name, message}` | 升级(结构化发送者+群号,服务端校验群白名单) |
| `/api/astrbot/stream` | GET(SSE) | `?token=` → `{seq, group, text}` 事件流 | **新增** |
| `/api/astrbot/messages` | GET | `?since=seq` → `{messages:[...]}` | **新增**(轮询 fallback) |

用户侧新增(JWT):`POST /api/user/qq/bind {code}`、`POST /api/user/qq/unbind`、`GET /api/user/qq/status → {bound, qq_masked, bound_at}`。游戏内通道新增(服务器间鉴权):`POST /internal/v1/qq/bind {player, code}`。实现后同步更新 `docs/API_CONTRACT.md`。

### 5.5 astrbot_plugin_dreamport(自研 AstrBot 插件)

- 位置:仓库顶层新目录 `astrbot-plugin/`(Python;原创实现,运行时仅调用 AstrBot 公开插件 API,不复制任何第三方源码——与 AstrBot 的 AGPL-3.0 保持「独立程序+运行时 API」关系,目录内附 LICENSE 说明)。
- 结构:`metadata.yaml`(name/desc/version/author,`astrbot_version` 约束)、`main.py`、`_conf_schema.json`(backend_url / api_token / forward_groups / cmd_prefix)、`README.md`(部署说明)。
- 行为:
  - 指令(`@filter.command`,前缀默认 `dp`):`帮助 / 绑定 / 解绑 / 查询 / 状态 / 玩家`,REST 调后端,失败给中文提示;
  - 群消息监听(`@filter.event_message_type(GROUP_MESSAGE)`):群在 `forward_groups` 中 → 过滤回环/指令/按 mode → POST `/api/astrbot/chat`(aiohttp,异常静默重试);
  - 下行 SSE 客户端:aiohttp 流式读 `/api/astrbot/stream?token=`,按 group 找到对应 umo(`platform:message_type:session_id`)→ `context.send_message(umo, MessageChain().message(text))`;断线指数退避重连;
  - 绑定码私聊送达:群内触发时用发送者 QQ 构造私聊 umo 发码,群内只提示。

### 5.6 Paper 插件改动(最小)

- `PluginConfig`:`features.receive-chat=true`、`features.message-poll-seconds=2`;
- `BackendClient`:`getPendingMessages(since)`;
- `ScheduledTasks`:新增收件箱轮询任务 → `Bukkit.broadcastMessage`;
- `dreamport-common`:新增 `PendingMessagesResponse` DTO + `Protocol` 常量。

### 5.7 管理后台

`Admin.vue` 新增「QQ 互通」设置卡:总开关、api_token(生成/复制)、群绑定列表编辑(群号/mode/前缀)、三个转发开关、模板编辑。走现有 `SystemSettingsController` CRUD(存 dp_setting,热生效)。

---

## 6. 实施计划(全部在 dev 分支,每里程碑一次提交)

| 里程碑 | 内容 | 可独立交付 |
|--------|------|-----------|
| M1 后端修复与门禁 | lookup bug 修复、移除免验证 bind、`astrbot.enabled` 门禁、status/players 契约定版、配置键 + config_help、管理后台设置卡 | ✅ 修复版本身就有价值 |
| M2 QQ 验证绑定 | BindCodeService(内存+频控+单测)、`bind/request`、用户侧三端点、游戏内通道(`/xmw qq bind` + `/internal/v1/qq/bind`)、Dashboard「QQ 绑定」卡 | ✅ |
| M3 互通后端 | ChatService origin 改造、QqBridgeService(出站队列/SSE/轮询)、群白名单校验 | ✅(配合 M4/M5 联调) |
| M4 Paper 插件下行 | 收件箱轮询 → 游戏内广播 + 配置 | ✅(先打通网页→游戏) |
| M5 AstrBot 插件 + 收尾 | astrbot-plugin 全量、API_CONTRACT/USER_GUIDE/OPERATIONS_CHECKLIST/CHANGELOG 更新、双端联调 | ✅ |

顺序理由:M1 独立清障;M2 是「验证」需求的主体且不依赖互通;M3→M4→M5 按数据流方向推进,每步可用 curl/网页先行验证,最后接真机 AstrBot。

## 7. 测试方案

- **单测**:绑定码生命周期(生成/过期/3 次作废/频控/覆盖)、单绑定语义(绑新清旧)、lookup 回归。
- **集成**:curl 脚本走通 ①申请码→网页确认→lookup 查得;②`/api/astrbot/chat` → 游戏收件箱出队;③SSE 流事件到达;④enabled=false 全端点 403。
- **真机联调清单**(需 AstrBot + NapCat 环境):群→服、服→群、网页→群/服、私聊收码、防回环(机器人消息不回流)、断线重连、群白名单外群不转发。
- **验收基线**:编译通过 + 既有测试全绿 + 冒烟启动正常。

## 8. 风险与说明

| 风险 | 应对 |
|------|------|
| 个人号协议端(NapCat 等)存在 QQ 风控可能 | 部署层风险;消息模板克制、可调低转发量;架构预留适配器扩展(官方机器人 API) |
| SSE 经反向代理被缓冲 | 复用已验证的 `X-Accel-Buffering: no` 方案;另有轮询 fallback |
| 内存队列重启丢失(聊天/绑定码) | 聊天属瞬态可接受;绑定码 5min TTL,重启后重新申请即可 |
| AstrBot 插件 API 版本演进 | metadata 声明 `astrbot_version` 约束,联调用当期稳定版 |
| 轮询 2s 延迟进服 | 聊天场景可接受;未来可升级复用 18899 WS 推送 |
| 许可谱系 | astrbot-plugin 为原创代码、仅运行时调用 AstrBot 公开 API,零源码复制,符合 Rules.md 红线 |

## 9. 决策点(已确认,2026-09-06)

1. **绑定确认通道**:网页输码 + **游戏内 `/xmw qq bind <码>` 备选通道都要**(已并入 §5.2 / M2)。
2. **群→服默认转发模式**:`all` 全部转发(群绑定可改 prefix)。
3. **免验证 `/api/astrbot/bind`**:移除,确认。
4. **网页聊天转发进群**:开启(`web_to_qq` 默认 true)。
5. **部署形态**:协议端为 NapCat;AstrBot 主机到后端 :18898 网络连通。
