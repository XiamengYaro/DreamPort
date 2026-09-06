package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.common.HeartbeatRequest;
import cn.xmcraft.dreamport.common.HeartbeatResponse;
import cn.xmcraft.dreamport.common.LoginCheckRequest;
import cn.xmcraft.dreamport.common.ErrorCode;
import cn.xmcraft.dreamport.server.config.WlProps;
import cn.xmcraft.dreamport.server.economy.EconomyService;
import cn.xmcraft.dreamport.server.qq.BindCodeService;
import cn.xmcraft.dreamport.server.qq.QqBindingService;
import cn.xmcraft.dreamport.server.qq.QqBridgeService;
import cn.xmcraft.dreamport.server.stats.ServerStatsService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.user.UserService;
import cn.xmcraft.dreamport.server.verification.MinecraftVerifyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务器间内部端点（鉴权：X-Server-Token，Rules.md §5）。
 * login-check / heartbeat / login-record / events / economy/snapshot / commands/whitelist。
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    private final UserService userService;
    private final WlProps props;
    private final ServerStatsService statsService;
    private final MinecraftVerifyService minecraftVerifyService;
    private final EconomyService economyService;
    private final cn.xmcraft.dreamport.server.chat.ChatService chatService;
    private final cn.xmcraft.dreamport.server.review.ReviewService reviewService;
    private final UserRepository userRepository;
    private final BindCodeService bindCodeService;
    private final QqBindingService qqBindingService;
    private final QqBridgeService qqBridge;

    public InternalController(UserService userService, WlProps props,
                              ServerStatsService statsService,
                              MinecraftVerifyService minecraftVerifyService,
                              EconomyService economyService,
                              cn.xmcraft.dreamport.server.chat.ChatService chatService,
                              cn.xmcraft.dreamport.server.review.ReviewService reviewService,
                              UserRepository userRepository,
                              BindCodeService bindCodeService,
                              QqBindingService qqBindingService,
                              QqBridgeService qqBridge) {
        this.userService = userService;
        this.props = props;
        this.statsService = statsService;
        this.minecraftVerifyService = minecraftVerifyService;
        this.economyService = economyService;
        this.chatService = chatService;
        this.reviewService = reviewService;
        this.userRepository = userRepository;
        this.bindCodeService = bindCodeService;
        this.qqBindingService = qqBindingService;
        this.qqBridge = qqBridge;
    }

    @PostMapping("/login-check")
    public ResponseEntity<Object> loginCheck(@RequestBody LoginCheckRequest req,
                                             HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (req == null || req.username() == null || req.username().isBlank()) {
            return badRequest("username 必填");
        }
        var decision = userService.loginDecision(req.username());
        if (decision.allowed()) {
            LOG.info("[校验] {} → 放行", req.username());
        } else {
            LOG.info("[校验] {} → 拒绝（{}）", req.username(), decision.reasonKey());
        }
        return ResponseEntity.ok(decision);
    }

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(InternalController.class);

    /** 各服上次心跳时间（用于离线检测） */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_SEEN = new java.util.concurrent.ConcurrentHashMap<>();

    @PostMapping("/heartbeat")
    public ResponseEntity<Object> heartbeat(@RequestBody HeartbeatRequest req,
                                            HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (req == null || req.serverId() == null || req.serverId().isBlank()) {
            return badRequest("serverId 必填");
        }
        long now = System.currentTimeMillis();
        String sid = req.serverId();
        Long prev = LAST_SEEN.put(sid, now);
        if (prev == null) {
            LOG.info("[连接] 服务器上线: {}（{}，{}）", sid, req.serverName(), req.role());
        }
        statsService.heartbeat(new ServerStatsService.Heartbeat(sid, req.serverName(),
                req.role(), req.onlinePlayers(), req.maxPlayers(), req.version(),
                req.players() == null ? java.util.List.of() : req.players(), now));
        if (now - (prev == null ? now : prev) >= 300_000) {
            // 每 5 分钟一条常规心跳摘要
            LOG.info("[心跳] {} 在线 {} / {}（玩家 {}）", sid, req.onlinePlayers(),
                    req.maxPlayers(), req.players() == null ? 0 : req.players().size());
        }
        return ResponseEntity.ok(new HeartbeatResponse(true, now));
    }

    /** 定时检测服务器离线（5 分钟无心跳视为离线） */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60_000)
    public void detectOffline() {
        long now = System.currentTimeMillis();
        LAST_SEEN.entrySet().removeIf(e -> {
            if (now - e.getValue() > 5 * 60_000L) {
                LOG.warn("[连接] 服务器离线: {}（超过 5 分钟无心跳）", e.getKey());
                return true;
            }
            return false;
        });
    }

    public record LoginRecordBody(String name, String uuid, String ip) {
    }

    /** QQ 绑定游戏内确认通道（/xmw qq bind <码>）：docs/ASTRBOT_PLAN.md §5.2 */
    public record QqBindBody(String player, String code) {
    }

    @PostMapping("/qq/bind")
    public ResponseEntity<Object> qqBind(@RequestBody QqBindBody body, HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (body == null || body.player() == null || body.code() == null || body.code().isBlank()) {
            return badRequest("player/code 必填");
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(body.player());
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByMinecraftNameIgnoreCase(body.player());
        }
        if (userOpt.isEmpty()) {
            return badRequest("账户不存在（需先注册白名单）");
        }
        var result = bindCodeService.consume(body.code().trim(), "game:" + body.player().toLowerCase());
        if (result.status() == BindCodeService.Status.LOCKED) {
            return badRequest("错误次数过多，请稍后再试");
        }
        if (result.status() == BindCodeService.Status.BAD_CODE) {
            return badRequest("验证码无效或已过期");
        }
        qqBindingService.bind(userOpt.get(), result.qq());
        LOG.info("[QQ绑定] {} 已绑定 QQ {}（游戏内确认）", userOpt.get().username(),
                QqBindingService.mask(result.qq()));
        return ResponseEntity.ok(cn.xmcraft.dreamport.server.web.ApiResponse.success("绑定成功",
                Map.of("qq", QqBindingService.mask(result.qq()))));
    }

    @PostMapping("/login-record")
    public ResponseEntity<Object> loginRecord(@RequestBody LoginRecordBody body,
                                              HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        minecraftVerifyService.recordLogin(body.name(), body.uuid(), body.ip());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public record EventBody(String type, String serverId, String player, String message) {
    }

    @PostMapping("/events")
    public ResponseEntity<Object> events(@RequestBody EventBody body, HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        switch (body.type() == null ? "" : body.type()) {
            case "chat" -> {
                chatService.broadcast("game", body.player() == null ? "?" : body.player(),
                        body.message() == null ? "" : body.message(), body.serverId());
                qqBridge.onGameChat(body.serverId(), body.player(), body.message());
            }
            case "join", "quit" -> {
                chatService.broadcast("system", "[系统]",
                        (body.player() == null ? "?" : body.player())
                                + ("join".equals(body.type()) ? " 加入了服务器" : " 离开了服务器"),
                        body.serverId());
                qqBridge.onGameEvent(body.serverId(), body.type(), body.player());
            }
            default -> {
                return badRequest("未知事件类型");
            }
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public record EconomySnapshotBody(List<EconomyService.PlayerEconomy> players) {
    }

    @PostMapping("/economy/snapshot")
    public ResponseEntity<Object> economySnapshot(@RequestBody EconomySnapshotBody body,
                                                  HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        economyService.saveSnapshot(body.players() == null ? List.of() : body.players());
        return ResponseEntity.ok(Map.of("ok", true, "count",
                body.players() == null ? 0 : body.players().size()));
    }

    @GetMapping("/commands/whitelist")
    public ResponseEntity<Object> whitelistCommands(HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        String serverId = request.getParameter("serverId");
        return ResponseEntity.ok(Map.of("commands",
                minecraftVerifyService.drainWhitelistCommands(serverId == null ? "main" : serverId)));
    }

    /** 游戏收件箱轮询：网页/QQ 消息下行进服（插件定时拉取后 broadcastMessage，docs/ASTRBOT_PLAN.md §5.3） */
    @GetMapping("/messages/pending")
    public ResponseEntity<Object> pendingMessages(HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        long since;
        try {
            since = Long.parseLong(request.getParameter("since"));
        } catch (NumberFormatException e) {
            since = 0;
        }
        var items = qqBridge.gameInboxSince(since);
        List<Map<String, Object>> messages = items.stream()
                .map(i -> Map.<String, Object>of("seq", i.seq(), "text", i.payload()))
                .toList();
        return ResponseEntity.ok(Map.of("messages", messages, "latest", qqBridge.gameInboxLatest()));
    }

    // ---------- 游戏内管理命令（/xmw 经服务器令牌调用，替代旧版进程内直调） ----------

    public record AdminOpBody(String username, String reason) {
    }

    @PostMapping("/admin-ops/{action}")
    public ResponseEntity<Object> adminOp(@PathVariable String action,
                                          @RequestBody AdminOpBody body,
                                          HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        String operator = "console@" + request.getHeader(
                cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_ID);
        var result = switch (action == null ? "" : action) {
            case "approve" -> reviewService.approve(body.username(), operator, "zh");
            case "reject" -> reviewService.reject(body.username(), operator,
                    body.reason() == null ? "未通过审核" : body.reason(), "zh");
            case "ban" -> reviewService.ban(body.username(), operator,
                    body.reason() == null ? "违规操作" : body.reason());
            case "unban" -> reviewService.unban(body.username(), operator);
            case "delete" -> reviewService.delete(body.username(), operator);
            default -> null;
        };
        if (result == null) {
            return badRequest("未知操作: " + action);
        }
        return result.success()
                ? ResponseEntity.ok(Map.of("ok", true, "message", result.message()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "message", result.message()));
    }

    @GetMapping("/admin-ops/list")
    public ResponseEntity<Object> adminList(HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        return ResponseEntity.ok(userService.pendingUsers());
    }

    @GetMapping("/admin-ops/info/{username}")
    public ResponseEntity<Object> adminInfo(@PathVariable String username,
                                            HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        return ResponseEntity.ok(userService.userInfo(username));
    }

    private ResponseEntity<Object> requireServerToken(HttpServletRequest request) {
        String token = request.getHeader(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_TOKEN);
        if (token == null || !props.internal().serverToken().equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", ErrorCode.TOKEN_MISSING.code(), "message", "服务器认证失败"));
        }
        return null;
    }

    private ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", 2001, "message", message));
    }
}
