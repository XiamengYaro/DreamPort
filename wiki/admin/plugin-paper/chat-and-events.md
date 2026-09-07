# Paper 主插件 · 聊天与事件互通

## 上报(游戏 → 后端)

| 事件 | 开关 | 驱动 |
|---|---|---|
| join / quit | `features.report-join-quit` | QQ 群进退服播报(按群开关)、官网"最近动态" |
| chat | `features.forward-chat` | QQ 群转发的游戏聊天、网页聊天室、聊天落库 |

上报通过 `POST /internal/v1/events`(type/player/message + serverId),聊天事件在异步聊天线程直接发送。

## 接收(后端 → 游戏)

后端把网页聊天、QQ 群消息渲染成文本,进入**游戏收件箱**;插件以 `features.message-poll-seconds`(默认 2s)轮询 `GET /internal/v1/messages/pending?since=<游标>`,新消息在主线程 `broadcastMessage`。

要点:

- 开关 `features.receive-chat`
- **游标机制**:插件本地 AtomicLong 记录已广播的 seq;**首次拉取只快进游标不广播**——插件重启不会把历史消息刷屏
- 后端队列有界(淘汰旧消息),游标落后过多时插件自动快进到 latest,防回放

## 消息格式

渲染模板在后端(AstrBot 群绑定配置 + 后端模板):

| 来源 | 游戏内看到 |
|---|---|
| QQ 群消息 | `[QQ] 昵称: 内容` |
| 网页聊天 | `[Web] 用户: 内容` |
| 进退服 | `玩家 加入了/离开了服务器` |

## 敏感词与限频

- 后端在聊天落库与 QQ 上行时应用 `sensitive.words`(替换 `***`)
- 网页聊天限频 10 条/分钟/用户;QQ 侧由群绑定决定转发范围

## 相关后端端点

| 端点 | 用途 |
|---|---|
| `POST /internal/v1/events` | 事件上报 |
| `GET /internal/v1/messages/pending?since=` | 收件箱增量拉取 |
