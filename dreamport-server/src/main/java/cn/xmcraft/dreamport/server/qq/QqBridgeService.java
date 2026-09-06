package cn.xmcraft.dreamport.server.qq;

import cn.xmcraft.dreamport.server.chat.ChatService;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.stats.ServerStatsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * QQ 群服互通桥（docs/ASTRBOT_PLAN.md §5.3）：
 * - 出站（服→群）：游戏聊天/进出服、网页聊天 → 模板渲染 → 出站队列 + SSE 推送机器人
 * - 入站（群→服）：群消息 → 群绑定/前缀校验 → 网页 SSE + 游戏收件箱
 * - 防回环：origin=qq 的入站不再写出站队列；机器人自身消息由插件侧 self_id 过滤
 * 队列容量 200，瞬态数据重启丢失可接受。
 */
@Service
public class QqBridgeService {

    public static final String KEY_GROUP_BINDINGS = "astrbot.group_bindings";
    public static final String KEY_FORWARD_GAME_TO_QQ = "astrbot.forward.game_to_qq";
    public static final String KEY_FORWARD_WEB_TO_QQ = "astrbot.forward.web_to_qq";
    public static final String KEY_FORWARD_QQ_TO_GAME = "astrbot.forward.qq_to_game";
    public static final String KEY_TPL_QQ_CHAT = "astrbot.template.qq_chat";
    public static final String KEY_TPL_QQ_JOIN = "astrbot.template.qq_join";
    public static final String KEY_TPL_QQ_QUIT = "astrbot.template.qq_quit";
    public static final String KEY_TPL_GAME_CHAT = "astrbot.template.game_chat";
    public static final String KEY_TPL_WEB_CHAT = "astrbot.template.web_chat";

    public record Outbound(String group, String text) {
    }

    /** 群绑定配置（astrbot.group_bindings JSON 数组元素） */
    public record GroupBinding(long group, String mode, String prefix, boolean forwardJoinQuit) {
    }

    private static final int CAPACITY = 200;
    private static final String DEFAULT_TPL_QQ_CHAT = "[{server}] {player}: {message}";
    private static final String DEFAULT_TPL_GAME_CHAT = "[QQ] {sender}: {message}";
    private static final String DEFAULT_TPL_WEB_CHAT = "[网页] {player}: {message}";

    private final SettingService settingService;
    private final ChatService chatService;
    private final ServerStatsService statsService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SeqQueue<Outbound> outbound = new SeqQueue<>(CAPACITY);
    private final SeqQueue<String> gameInbox = new SeqQueue<>(CAPACITY);
    private final List<SseEmitter> botEmitters = new CopyOnWriteArrayList<>();

    public QqBridgeService(SettingService settingService, ChatService chatService,
                           ServerStatsService statsService) {
        this.settingService = settingService;
        this.chatService = chatService;
        this.statsService = statsService;
    }

    // ---------- 事件入口（由各控制器调用） ----------

    /** 游戏聊天 → 群 */
    public void onGameChat(String serverId, String player, String message) {
        if (!settingService.getBool(KEY_FORWARD_GAME_TO_QQ, true)) {
            return;
        }
        String server = resolveServerName(serverId);
        for (GroupBinding b : bindings()) {
            String text = render(template(KEY_TPL_QQ_CHAT, DEFAULT_TPL_QQ_CHAT),
                    Map.of("server", server, "player", player == null ? "?" : player,
                            "message", message == null ? "" : message));
            offerOutbound(b, text);
        }
    }

    /** 游戏玩家进服/退服 → 群 */
    public void onGameEvent(String serverId, String type, String player) {
        if (!settingService.getBool(KEY_FORWARD_GAME_TO_QQ, true)) {
            return;
        }
        String name = player == null ? "?" : player;
        String server = resolveServerName(serverId);
        for (GroupBinding b : bindings()) {
            if (!b.forwardJoinQuit()) {
                continue;
            }
            String tpl = "join".equals(type)
                    ? template(KEY_TPL_QQ_JOIN, "{player} 加入了服务器")
                    : template(KEY_TPL_QQ_QUIT, "{player} 离开了服务器");
            offerOutbound(b, render(tpl, Map.of("player", name, "server", server)));
        }
    }

    /** 网页聊天 → 游戏收件箱 + 群 */
    public void onWebChat(String player, String message) {
        String name = player == null ? "网页" : player;
        gameInbox.offer(render(template(KEY_TPL_WEB_CHAT, DEFAULT_TPL_WEB_CHAT),
                Map.of("player", name, "message", message == null ? "" : message)));
        if (!settingService.getBool(KEY_FORWARD_WEB_TO_QQ, true)) {
            return;
        }
        for (GroupBinding b : bindings()) {
            String text = render(template(KEY_TPL_QQ_CHAT, DEFAULT_TPL_QQ_CHAT),
                    Map.of("server", "网页", "player", name,
                            "message", message == null ? "" : message));
            offerOutbound(b, text);
        }
    }

    /**
     * QQ 群消息进服。@return 错误提示（返回给调用方）；null 表示已放行或按配置静默忽略。
     */
    public String onQqGroupMessage(long group, String senderId, String senderName, String message) {
        if (!settingService.getBool(KEY_FORWARD_QQ_TO_GAME, true)) {
            return "群服互通未开启（astrbot.forward.qq_to_game）";
        }
        GroupBinding binding = bindings().stream().filter(b -> b.group() == group).findFirst().orElse(null);
        if (binding == null) {
            return "该群未参与互通（管理后台 → QQ 互通 → 群绑定）";
        }
        String content = message == null ? "" : message;
        if ("prefix".equalsIgnoreCase(binding.mode())) {
            String prefix = binding.prefix() == null ? "" : binding.prefix();
            if (prefix.isEmpty() || !content.startsWith(prefix)) {
                return null;
            }
            content = content.substring(prefix.length()).trim();
        }
        if (content.isBlank()) {
            return null;
        }
        String display = senderName == null || senderName.isBlank()
                ? (senderId == null || senderId.isBlank() ? "QQ用户" : senderId)
                : senderName;
        chatService.broadcast("qq", "[QQ] " + display, content, null);
        gameInbox.offer(render(template(KEY_TPL_GAME_CHAT, DEFAULT_TPL_GAME_CHAT),
                Map.of("sender", display, "message", content)));
        return null;
    }

    // ---------- 机器人下行 ----------

    /** 机器人 SSE 订阅（/api/astrbot/stream） */
    public SseEmitter subscribeBot() {
        SseEmitter emitter = new SseEmitter(0L);
        botEmitters.add(emitter);
        emitter.onCompletion(() -> botEmitters.remove(emitter));
        emitter.onTimeout(() -> botEmitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "connected")));
        } catch (Exception e) {
            botEmitters.remove(emitter);
        }
        return emitter;
    }

    public List<SeqQueue.Item<Outbound>> outboundSince(long since) {
        return outbound.since(since);
    }

    public long outboundLatest() {
        return outbound.latest();
    }

    public List<SeqQueue.Item<String>> gameInboxSince(long since) {
        return gameInbox.since(since);
    }

    public long gameInboxLatest() {
        return gameInbox.latest();
    }

    // ---------- 内部 ----------

    private void offerOutbound(GroupBinding b, String text) {
        Outbound payload = new Outbound(String.valueOf(b.group()), text);
        SeqQueue.Item<Outbound> item = outbound.offer(payload);
        if (botEmitters.isEmpty()) {
            return;
        }
        String json;
        try {
            json = mapper.writeValueAsString(Map.of("seq", item.seq(),
                    "group", payload.group(), "text", payload.text()));
        } catch (Exception e) {
            return;
        }
        for (SseEmitter emitter : botEmitters) {
            try {
                emitter.send(SseEmitter.event().data(json));
            } catch (Exception e) {
                botEmitters.remove(emitter);
            }
        }
    }

    List<GroupBinding> bindings() {
        return parseBindings(settingService.get(KEY_GROUP_BINDINGS, List.class));
    }

    /** 解析群绑定配置（容错：非法条目跳过） */
    public static List<GroupBinding> parseBindings(List<Object> raw) {
        if (raw == null) {
            return List.of();
        }
        List<GroupBinding> result = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            try {
                long group = Long.parseLong(String.valueOf(m.get("group")));
                String mode = m.get("mode") == null ? "all" : String.valueOf(m.get("mode"));
                String prefix = m.get("prefix") == null ? "#" : String.valueOf(m.get("prefix"));
                boolean fjq = m.get("forward_join_quit") == null
                        || Boolean.parseBoolean(String.valueOf(m.get("forward_join_quit")));
                result.add(new GroupBinding(group, mode, prefix, fjq));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private String template(String key, String def) {
        String v = settingService.get(key, String.class);
        return v == null || v.isBlank() ? def : v;
    }

    private String resolveServerName(String serverId) {
        if (serverId != null) {
            var hb = statsService.heartbeats().get(serverId);
            if (hb != null && hb.serverName() != null && !hb.serverName().isBlank()) {
                return hb.serverName();
            }
            return serverId;
        }
        return "服务器";
    }

    /**
     * 模板渲染：{key} 占位符字面替换。
     * 单遍扫描——替换进来的值不会被再次扫描，消息内容含 "{player}" 等文本时保持原样。
     */
    public static String render(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(template.length() + 32);
        int i = 0;
        while (i < template.length()) {
            int open = template.indexOf('{', i);
            if (open < 0) {
                sb.append(template, i, template.length());
                break;
            }
            int close = template.indexOf('}', open);
            if (close < 0) {
                sb.append(template, i, template.length());
                break;
            }
            String key = template.substring(open + 1, close);
            if (vars.containsKey(key)) {
                sb.append(template, i, open);
                String value = vars.get(key);
                sb.append(value == null ? "" : value);
                i = close + 1;
            } else {
                sb.append(template, i, close + 1);
                i = close + 1;
            }
        }
        return sb.toString();
    }
}
