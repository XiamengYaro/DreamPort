# AstrBot 插件 · 简介与安装

**astrbot_plugin_dreamport** 是 DreamPort 官方的 AstrBot 对接插件(Python,MIT),把 QQ 群变成服务器门户的"前台":

- 🔐 **QQ 验证绑定**:玩家在群里申请验证码,私聊接收,网页或游戏内确认绑定
- 💬 **群服消息互通**:指定 QQ 群 ↔ 游戏内聊天(双向,实时)
- 📊 **查询指令**:服务器状态/在线玩家/绑定查询
- 📣 **下行推送**:进退服、公告、游戏/网页聊天按模板实时发群

## 通信架构

```
QQ 用户 ⇄ NapCat(OneBot v11) ⇄ AstrBot(aiocqhttp 适配器)
                                   ⇄ astrbot_plugin_dreamport
                                        │ REST + SSE
                                        ↓
                              DreamPort 后端(:18898)
                                        ↑ 事件/收件箱
                              MC 服务器(dreamport-plugin)
```

- 上行:群消息/指令 → REST `POST /api/astrbot/**`
- 下行:游戏/网页消息 → SSE `GET /api/astrbot/stream`(长连,断线指数退避重连 3→60s)
- 插件**不做轮询**;后端重启后验证码与队列丢失(内存态)属预期

## 安装

1. 准备 AstrBot 与 QQ 协议端(如 NapCat),协议端经 **OneBot v11 反向 WS** 接入 AstrBot 的 aiocqhttp 适配器(默认端口 6199)
2. 安装插件(任选):
   - 把 `astrbot-plugin/` 目录放到 AstrBot `data/plugins/astrbot_plugin_dreamport/`
   - 或 AstrBot WebUI → 插件市场 → 本地安装
3. 重启 AstrBot,依赖 `aiohttp>=3.9` 自动安装
4. 看日志出现 **`DreamPort 插件加载自检: backend=… | api_token=… | platform_id=… | forward_groups=…`** 即加载成功

## 卸载/更新

WebUI 停用/替换目录即可;`_terminate()` 会干净取消 SSE 任务。

## 版本

- v1.0.1(当前):修复配置注入与 logger;`platform_id` 默认 `default`
- 仅依赖 AstrBot 公开插件 API,MIT 许可
