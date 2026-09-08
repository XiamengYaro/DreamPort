# 全组件故障排查速查

按"现象 → 检查点"组织;细节见各插件文档对应篇目。

## 后端

| 现象 | 检查 |
|---|---|
| 启动即失败 | 终端 ✗ 提示:MySQL 连接/端口占用/config.yml 语法;日志模式确认 Flyway 状态 |
| 邮件发不出 | SMTP 未配置=日志模式(验证码打在日志);失败查授权码/端口/SSL(465 vs 587) |
| 验证码收不到 | 同上;日志 `grep 验证码已生成` 确认生成 |
| 前端样式异常 | 浏览器强刷;确认 JAR 内 static 为最新构建 |
| 管理后台进不去 | 登录账号须在「系统配置 → 管理员名单」 |
| WebSocket 不通 | `ws-port`(18899)是否被防火墙/反代转发 |

## Paper 主插件

| 现象 | 检查 |
|---|---|
| `/xmw status` 显示后端不可达 | `backend.url`;后端进程;防火墙 |
| 所有人被踢"未注册"但后端正常 | `server-token` 与后端不一致;`server-id` 冲突 |
| 后端短暂宕机全服被踢 | `check.fail-policy` 改 `cache`/`allow`(权衡安全) |
| 白名单状态改了没生效 | 校验结果缓存 `cache-ttl-seconds`(默认 60s) |
| 聊天/进退服没进群 | `forward-chat`/`report-join-quit`;AstrBot 侧配置(见下) |
| 网页消息没广播进游戏 | `receive-chat`;后端收件箱队列是否堆积 |
| 经济数据没上报 | `economy.report`(auto 仅主服);Vault/EssentialsX 是否就绪 |
| Folia 服报调度错误 | 确认使用 1.1.0+(AsyncScheduler) |

## Velocity 代理插件

| 现象 | 检查 |
|---|---|
| 子服插件还在各自拦截 | 子服 `role` 改 `secondary`/`proxy`,拦截交给代理 |
| 踢出文案想改 | 目前硬编码中文,需改插件源码或等 i18n 版本 |
| 心跳频率想调 | 当前固定 60s(`heartbeat-interval` 键未读取) |
| 代理后服务器列表少数据 | 代理心跳 serverId=proxy;子服各自心跳也需开启 |

## AstrBot 插件

| 现象 | 检查 |
|---|---|
| 所有指令"后端不可达" | `backend_url`;后端进程;`astrbot.enabled`(关闭=403) |
| 自检日志 api_token `!!` | 令牌未配置(管理后台 QQ 互通生成) |
| 指令正常,群消息不转发 | 群号不在 `forward_groups`;后端群绑定 JSON 不一致 |
| 验证码私聊失败 | `platform_id` 与适配器 ID 不一致;玩家隐私设置 |
| 服→群没消息 | SSE 未连接(看日志);群号;后端模板;`forward_join_quit` |
| SSE 反复重连 | 令牌错误(403)或网络断;退避最长 60s |
| 机器人消息回流游戏 | 不应发生(防回环);出现说明用了非插件通道发群 |

## 通用

| 现象 | 检查 |
|---|---|
| 某组件连不上后端 | 先 `curl http://后端:18898/api/health`;再对令牌;再看后端 `[访问]` 日志有没有请求到达 |
| 需要看后端在干什么 | 日志前缀:`[访问]` `[校验]` `[连接]` `[心跳]` `[Mail]` `[迁移]` `[修改]`(完整见 OPERATIONS_CHECKLIST) |
| 数据库迁移失败 | DROP 半建表 + 删 flyway_schema_history 失败行,重启(见后端数据库篇) |
