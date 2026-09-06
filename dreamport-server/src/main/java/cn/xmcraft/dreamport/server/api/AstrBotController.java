package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.chat.ChatService;
import cn.xmcraft.dreamport.server.qq.BindCodeService;
import cn.xmcraft.dreamport.server.qq.QqBindingService;
import cn.xmcraft.dreamport.server.qq.QqBridgeService;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.stats.ServerStatsService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AstrBot 机器人端点（v1.1 契约，docs/ASTRBOT_PLAN.md §5.4，X-API-Token 鉴权）：
 * 服务器状态、玩家列表、QQ 绑定验证码/查询/解绑、群服消息互通（上行 chat + 下行 stream/messages）。
 * astrbot.enabled 关闭时全部 403。
 */
@RestController
@RequestMapping("/api/astrbot")
public class AstrBotController {

    private final UserRepository userRepository;
    private final ChatService chatService;
    private final ServerStatsService statsService;
    private final SettingService settingService;
    private final BindCodeService bindCodeService;
    private final QqBindingService bindingService;
    private final QqBridgeService qqBridge;

    public AstrBotController(UserRepository userRepository, ChatService chatService,
                             ServerStatsService statsService, SettingService settingService,
                             BindCodeService bindCodeService, QqBindingService bindingService,
                             QqBridgeService qqBridge) {
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.statsService = statsService;
        this.settingService = settingService;
        this.bindCodeService = bindCodeService;
        this.bindingService = bindingService;
        this.qqBridge = qqBridge;
    }

    public record UnbindBody(String qq) {
    }

    public record BindRequestBody(String qq) {
    }

    public record ChatBody(Long group,
                           @JsonProperty("sender_id") String senderId,
                           @JsonProperty("sender_name") String senderName,
                           String message) {
    }

    /** 总开关：关闭时先于 token 校验返回 403，不泄露 token 有效性 */
    private boolean enabled() {
        return settingService.getBool(SettingService.KEY_ASTRBOT_ENABLED, false);
    }

    private boolean authorized(HttpServletRequest request) {
        String token = request.getHeader("X-API-Token");
        String expected = settingService.get(SettingService.KEY_ASTRBOT_TOKEN, String.class);
        return token != null && expected != null && !expected.isBlank()
                && token.replace("\"", "").equals(expected.replace("\"", ""));
    }

    private ResponseEntity<Object> denied() {
        return ResponseEntity.status(401).body(ApiResponse.failure("X-API-Token 无效（请在管理后台设置 astrbot.api_token）"));
    }

    private ResponseEntity<Object> disabled() {
        return ResponseEntity.status(403).body(ApiResponse.failure("AstrBot 集成未开启（管理后台 → QQ 互通）"));
    }

    private ResponseEntity<Object> gate(HttpServletRequest request) {
        if (!enabled()) {
            return disabled();
        }
        if (!authorized(request)) {
            return denied();
        }
        return null;
    }

    @GetMapping("/status")
    public ResponseEntity<Object> status(HttpServletRequest request) {
        ResponseEntity<Object> blocked = gate(request);
        if (blocked != null) {
            return blocked;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("online", statsService.totalOnline());
        body.put("max", statsService.maxPlayers());
        body.put("servers", statsService.totalServers());
        body.put("version", statsService.version());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/players")
    public ResponseEntity<Object> players(HttpServletRequest request) {
        ResponseEntity<Object> blocked = gate(request);
        if (blocked != null) {
            return blocked;
        }
        List<Map<String, String>> players = new ArrayList<>();
        for (var hb : statsService.heartbeats().values()) {
            String server = hb.serverName() == null ? hb.serverId() : hb.serverName();
            for (String name : hb.players()) {
                players.add(Map.of("name", name, "server", server));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", players.size());
        body.put("players", players);
        body.put("servers", statsService.heartbeats().values());
        return ResponseEntity.ok(body);
    }

    /** 验证码绑定第一步：QQ 侧申请验证码（机器人私聊送达用户,网页/游戏内凭码完成绑定） */
    @PostMapping("/bind/request")
    public ResponseEntity<Object> bindRequest(@RequestBody BindRequestBody body, HttpServletRequest request) {
        ResponseEntity<Object> blocked = gate(request);
        if (blocked != null) {
            return blocked;
        }
        if (body == null || body.qq() == null || body.qq().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("缺少 qq 参数"));
        }
        BindCodeService.Issue issue = bindCodeService.request(body.qq().trim());
        if (issue == null) {
            return ResponseEntity.status(429).body(ApiResponse.failure("申请过于频繁（每分钟 1 次、每天 5 次）"));
        }
        return ResponseEntity.ok(ApiResponse.success("验证码已生成，请通过 QQ 私聊发送给用户",
                Map.of("code", issue.code(), "expires_in", issue.expiresIn())));
    }

    @PostMapping("/unbind")
    public ResponseEntity<Object> unbind(@RequestBody UnbindBody body, HttpServletRequest request) {
        ResponseEntity<Object> blocked = gate(request);
        if (blocked != null) {
            return blocked;
        }
        if (body.qq() == null || body.qq().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("缺少 qq 参数"));
        }
        return ResponseEntity.ok(bindingService.unbindQq(body.qq())
                ? ApiResponse.success("已解绑")
                : ApiResponse.failure("该 QQ 未绑定任何账号"));
    }

    @GetMapping("/lookup/qq/{qq}")
    public ResponseEntity<Object> lookupQq(@PathVariable String qq, HttpServletRequest request) {
        ResponseEntity<Object> blocked = gate(request);
        if (blocked != null) {
            return blocked;
        }
        return ResponseEntity.ok(userRepository.listAll().stream()
                .filter(u -> qq.equals(u.qqNumber()))
                .findFirst()
                .map(u -> Map.of("found", true, "username", u.username(), "status", u.status()))
                .orElseGet(() -> Map.of("found", false)));
    }

    @GetMapping("/lookup/mc/{mc}")
    public ResponseEntity<Object> lookupMc(@PathVariable String mc, HttpServletRequest request) {
        ResponseEntity<Object> blocked = gate(request);
        if (blocked != null) {
            return blocked;
        }
        return ResponseEntity.ok(userRepository.listAll().stream()
                .filter(u -> mc.equalsIgnoreCase(u.username()) || mc.equalsIgnoreCase(u.minecraftName()))
                .findFirst()
                .map(u -> {
                    boolean bound = u.qqNumber() != null && !u.qqNumber().isBlank();
                    return Map.of("found", true, "qq", bound ? u.qqNumber() : "", "bound", bound);
                })
                .orElseGet(() -> Map.of("found", false)));
    }

    /** 群消息上行（群→服）：群绑定/前缀校验 → 网页 SSE + 游戏收件箱 */
    @PostMapping("/chat")
    public ResponseEntity<Object> chat(@RequestBody ChatBody body, HttpServletRequest request) {
        ResponseEntity<Object> blocked = gate(request);
        if (blocked != null) {
            return blocked;
        }
        if (body == null || body.group() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("缺少 group 参数"));
        }
        if (body.message() == null || body.message().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("消息为空"));
        }
        if (body.message().length() > 512) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("消息过长（上限 512 字）"));
        }
        String error = qqBridge.onQqGroupMessage(body.group(), body.senderId(),
                body.senderName(), body.message());
        if (error != null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(error));
        }
        return ResponseEntity.ok(ApiResponse.success("已广播"));
    }

    /** 下行 SSE 流（服→群，事件 {seq, group, text}）；反代缓冲禁用（v0.5.22 同款方案） */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-store");
        if (!enabled() || !authorized(request)) {
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalStateException("未启用或 X-API-Token 无效"));
            return emitter;
        }
        return qqBridge.subscribeBot();
    }

    /** 下行轮询 fallback（SSE 不可用时），?since=seq → {messages, latest} */
    @GetMapping("/messages")
    public ResponseEntity<Object> messages(@RequestParam(required = false, defaultValue = "0") long since,
                                           HttpServletRequest request) {
        ResponseEntity<Object> blocked = gate(request);
        if (blocked != null) {
            return blocked;
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (var item : qqBridge.outboundSince(since)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", item.seq());
            m.put("group", item.payload().group());
            m.put("text", item.payload().text());
            messages.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", messages);
        body.put("latest", qqBridge.outboundLatest());
        return ResponseEntity.ok(body);
    }
}
