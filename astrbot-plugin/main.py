# DreamPort QQ 互通插件(astrbot_plugin_dreamport)
# 原创实现(MIT License,随 DreamPort 仓库发布),运行时仅调用 AstrBot 公开插件 API,
# 不包含任何第三方项目源码——代码谱系见 DreamPort 仓库 AGENTS.md / Rules.md。
#
# 功能:
#   1. QQ 验证绑定:/dp 绑定 → 后端生成一次性验证码 → 私聊送达 → 网页/游戏内确认
#   2. 群服消息互通:绑定群消息上行后端;后端 SSE 下行实时发群
#   3. 服务器状态/玩家查询:/dp 状态、/dp 玩家
import asyncio
import json
import logging

import aiohttp
from astrbot.api.event import AstrMessageEvent, MessageChain, filter
from astrbot.api.star import Context, Star, register

logger = logging.getLogger("astrbot.plugin.dreamport")

HELP_TEXT = (
    "DreamPort 指令(夏日小镇·梦港):\n"
    "/dp 绑定 —— 获取 QQ 绑定验证码(私聊发送,5 分钟有效)\n"
    "/dp 解绑 —— 解除当前 QQ 的绑定\n"
    "/dp 查询 —— 查看本 QQ 的绑定状态\n"
    "/dp 状态 —— 服务器在线状态\n"
    "/dp 玩家 —— 在线玩家列表\n"
    "/dp 帮助 —— 显示本帮助"
)


@register(
    "astrbot_plugin_dreamport",
    "XiaMeng",
    "DreamPort(夏日小镇·梦港)QQ 验证绑定与群服消息互通",
    "1.0.0",
)
class DreamPortPlugin(Star):

    def __init__(self, context: Context):
        super().__init__(context)
        self._sse_task = None
        self._stopping = False
        self._reconnect_delay = 3
        self._umo_cache = {}  # 群号 → 实际会话 umo(来自真实消息,优先于构造 umo)

    # ---------- 配置(惰性读取,后台改配置重载后即生效) ----------

    def _setting(self, key: str, default: str) -> str:
        cfg = getattr(self, "config", None)
        try:
            value = cfg.get(key, default) if cfg else default
        except Exception:
            value = default
        value = str(value if value is not None else default).strip()
        return value or default

    def _backend_url(self) -> str:
        return self._setting("backend_url", "http://127.0.0.1:18898").rstrip("/")

    def _api_token(self) -> str:
        return self._setting("api_token", "")

    def _platform_id(self) -> str:
        return self._setting("platform_id", "aiocqhttp")

    def _forward_groups(self) -> set:
        raw = self._setting("forward_groups", "")
        return {g.strip() for g in raw.split(",") if g.strip()}

    # ---------- HTTP ----------

    def _headers(self) -> dict:
        return {"X-API-Token": self._api_token(), "Content-Type": "application/json"}

    async def _request_json(self, method: str, path: str, payload=None):
        """@return (http_status, body_json);网络失败返回 (0, {})"""
        try:
            timeout = aiohttp.ClientTimeout(total=6)
            async with aiohttp.ClientSession(timeout=timeout) as session:
                async with session.request(method, self._backend_url() + path,
                                           json=payload, headers=self._headers()) as resp:
                    try:
                        data = await resp.json(content_type=None)
                    except Exception:
                        data = {}
                    return resp.status, data
        except Exception as e:
            logger.warning("后端请求失败 %s %s: %s", method, path, e)
            return 0, {}

    @staticmethod
    def _err_text(data: dict, default: str) -> str:
        msg = data.get("message") or data.get("msg") if isinstance(data, dict) else None
        return str(msg) if msg else default

    # ---------- 指令 ----------

    @filter.command("dp")
    async def dp(self, event: AstrMessageEvent):
        """/dp 指令入口"""
        try:
            parts = ((event.message_str or "").strip()).split()
            while parts and parts[0].lstrip("/").lower() in ("dp", "dreamport"):
                parts = parts[1:]
            action = parts[0] if parts else "help"
            handlers = {
                "help": self._cmd_help, "帮助": self._cmd_help,
                "绑定": self._cmd_bind, "bind": self._cmd_bind,
                "解绑": self._cmd_unbind, "unbind": self._cmd_unbind,
                "查询": self._cmd_lookup, "lookup": self._cmd_lookup,
                "状态": self._cmd_status, "status": self._cmd_status,
                "玩家": self._cmd_players, "players": self._cmd_players,
            }
            handler = handlers.get(action)
            if handler is None:
                yield event.plain_result(f"未知指令「{action}」\n{HELP_TEXT}")
                return
            yield event.plain_result(await handler(event))
        except Exception as e:
            logger.exception("dp 指令处理失败")
            yield event.plain_result(f"指令执行出错:{e}")

    async def _cmd_help(self, event: AstrMessageEvent) -> str:
        return HELP_TEXT

    async def _cmd_bind(self, event: AstrMessageEvent) -> str:
        sender_id = str(event.get_sender_id() or "")
        if not sender_id:
            return "无法识别你的 QQ 号"
        status, data = await self._request_json(
            "POST", "/api/astrbot/bind/request", {"qq": sender_id})
        if status == 429:
            return "申请过于频繁(每分钟 1 次、每天 5 次),请稍后再试"
        if status == 0:
            return "后端不可达,请稍后再试"
        if status != 200:
            return self._err_text(data, "验证码申请失败")
        code = (data.get("data") or {}).get("code", "") if isinstance(data, dict) else ""
        notice = (f"你的 DreamPort 绑定验证码:{code}(5 分钟内有效)\n"
                  "网页「个人中心 → QQ 绑定」输入,或游戏内执行 /xmw qq bind "
                  + code)
        if not event.get_group_id():
            return notice
        if await self._send_umo(f"{self._platform_id()}:FriendMessage:{sender_id}", notice):
            return "验证码已私聊发送给你(5 分钟内有效),请按私聊提示完成绑定"
        return ("验证码私聊发送失败——请私聊我发送「dp 绑定」重新获取"
                "(如持续失败请联系管理员检查 platform_id 配置)")

    async def _cmd_unbind(self, event: AstrMessageEvent) -> str:
        sender_id = str(event.get_sender_id() or "")
        if not sender_id:
            return "无法识别你的 QQ 号"
        status, data = await self._request_json(
            "POST", "/api/astrbot/unbind", {"qq": sender_id})
        if status == 0:
            return "后端不可达,请稍后再试"
        if status == 200:
            return "已解绑"
        return self._err_text(data, "该 QQ 未绑定任何账号")

    async def _cmd_lookup(self, event: AstrMessageEvent) -> str:
        sender_id = str(event.get_sender_id() or "")
        if not sender_id:
            return "无法识别你的 QQ 号"
        status, data = await self._request_json(
            "GET", f"/api/astrbot/lookup/qq/{sender_id}")
        if status != 200:
            return self._err_text(data, "查询失败")
        if not data.get("found"):
            return "你的 QQ 尚未绑定 MC 账号(发送 /dp 绑定 开始)"
        return f"你的 QQ 绑定了账号:{data.get('username')}(状态:{data.get('status')})"

    async def _cmd_status(self, event: AstrMessageEvent) -> str:
        status, data = await self._request_json("GET", "/api/astrbot/status")
        if status == 0:
            return "后端不可达,请稍后再试"
        if status != 200:
            return self._err_text(data, "状态查询失败")
        version = data.get("version") or "未知"
        return (f"服务器在线:{data.get('online', 0)}/{data.get('max', 0)}"
                f" · 群组 {data.get('servers', 0)} 服 · 版本 {version}")

    async def _cmd_players(self, event: AstrMessageEvent) -> str:
        status, data = await self._request_json("GET", "/api/astrbot/players")
        if status == 0:
            return "后端不可达,请稍后再试"
        if status != 200:
            return self._err_text(data, "玩家查询失败")
        count = data.get("count", 0)
        if not count:
            return "当前没有玩家在线"
        names = ["· " + p.get("name", "?") + "(" + p.get("server", "?") + ")"
                 for p in (data.get("players") or [])[:30]]
        return f"在线玩家 {count} 人:\n" + "\n".join(names)

    # ---------- 群消息上行(群→服) ----------

    @filter.event_message_type(filter.EventMessageType.GROUP_MESSAGE)
    async def on_group_message(self, event: AstrMessageEvent):
        try:
            gid = str(event.get_group_id() or "")
            if not gid or gid not in self._forward_groups():
                return
            text = (event.message_str or "").strip()
            if not text:
                return
            # 指令不转发(平台唤醒前缀已剥离,再兜底匹配指令名)
            first = text.split()[0].lstrip("/").lower()
            if text.startswith("/") or first in ("dp", "dreamport"):
                return
            sender_id = str(event.get_sender_id() or "")
            self_id = str(getattr(event.message_obj, "self_id", "") or "")
            if sender_id and sender_id == self_id:
                return  # 防回环:机器人自己的消息
            self._umo_cache[gid] = event.unified_msg_origin
            sender_name = event.get_sender_name() or sender_id
            await self._post_chat(gid, sender_id, sender_name, text)
            self._ensure_sse()
        except Exception:
            logger.exception("群消息转发失败")

    async def _post_chat(self, group: str, sender_id: str, sender_name: str, message: str):
        status, data = await self._request_json("POST", "/api/astrbot/chat", {
            "group": int(group) if group.isdigit() else group,
            "sender_id": sender_id,
            "sender_name": sender_name,
            "message": message,
        })
        if status not in (200, 0):
            logger.debug("群 %s 消息未转发(HTTP %s): %s", group, status, data)

    # ---------- 下行 SSE(服→群) ----------

    def _ensure_sse(self):
        if self._stopping or not self._api_token():
            return
        if self._sse_task is None or self._sse_task.done():
            self._sse_task = asyncio.ensure_future(self._sse_loop())

    async def _sse_loop(self):
        logger.info("DreamPort 下行 SSE 已启动(后端 %s)", self._backend_url())
        while not self._stopping:
            try:
                timeout = aiohttp.ClientTimeout(total=None, connect=5, sock_read=90)
                async with aiohttp.ClientSession(timeout=timeout) as session:
                    async with session.get(
                            f"{self._backend_url()}/api/astrbot/stream",
                            headers={"X-API-Token": self._api_token()}) as resp:
                        if resp.status != 200:
                            logger.warning("SSE 连接失败:HTTP %s", resp.status)
                        else:
                            self._reconnect_delay = 3
                            async for raw in resp.content:
                                if self._stopping:
                                    break
                                line = raw.decode("utf-8", "ignore").strip()
                                if line.startswith("data:"):
                                    await self._handle_sse_data(line[5:].strip())
            except asyncio.CancelledError:
                raise
            except Exception as e:
                logger.warning("SSE 中断:%s(%ss 后重连)", e, self._reconnect_delay)
            if not self._stopping:
                await asyncio.sleep(self._reconnect_delay)
                self._reconnect_delay = min(self._reconnect_delay * 2, 60)

    async def _handle_sse_data(self, payload: str):
        if not payload:
            return
        try:
            data = json.loads(payload)
        except Exception:
            return
        if data.get("type") == "connected":
            logger.info("DreamPort 下行 SSE 已连接")
            return
        group = str(data.get("group", ""))
        text = str(data.get("text", ""))
        if group and text:
            await self._send_group(group, text)

    # ---------- 发消息 ----------

    async def _send_group(self, group: str, text: str):
        umo = self._umo_cache.get(group) or f"{self._platform_id()}:GroupMessage:{group}"
        await self._send_umo(umo, text)

    async def _send_umo(self, umo: str, text: str) -> bool:
        try:
            await self.context.send_message(umo, MessageChain().message(text))
            return True
        except Exception as e:
            logger.warning("消息发送失败(%s): %s", umo, e)
            return False

    async def terminate(self):
        self._stopping = True
        if self._sse_task:
            self._sse_task.cancel()
