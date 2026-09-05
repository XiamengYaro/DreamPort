package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.common.HeartbeatRequest;
import cn.xmcraft.dreamport.common.HeartbeatResponse;
import cn.xmcraft.dreamport.common.LoginCheckRequest;
import cn.xmcraft.dreamport.common.ErrorCode;
import cn.xmcraft.dreamport.server.config.WlProps;
import cn.xmcraft.dreamport.server.economy.EconomyService;
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

    public InternalController(UserService userService, WlProps props,
                              ServerStatsService statsService,
                              MinecraftVerifyService minecraftVerifyService,
                              EconomyService economyService,
                              cn.xmcraft.dreamport.server.chat.ChatService chatService,
                              cn.xmcraft.dreamport.server.review.ReviewService reviewService,
                              UserRepository userRepository) {
        this.userService = userService;
        this.props = props;
        this.statsService = statsService;
        this.minecraftVerifyService = minecraftVerifyService;
        this.economyService = economyService;
        this.chatService = chatService;
        this.reviewService = reviewService;
        this.userRepository = userRepository;
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
        // 群组服统一拦截（proxy 角色）时也走同一决策
        return ResponseEntity.ok(userService.loginDecision(req.username()));
    }

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
        statsService.heartbeat(new ServerStatsService.Heartbeat(req.serverId(), req.serverName(),
                req.role(), req.onlinePlayers(), req.maxPlayers(), req.version(),
                req.players() == null ? java.util.List.of() : req.players(),
                System.currentTimeMillis()));
        return ResponseEntity.ok(new HeartbeatResponse(true, System.currentTimeMillis()));
    }

    public record LoginRecordBody(String name, String uuid, String ip) {
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
            case "chat" -> chatService.broadcast(body.player() == null ? "?" : body.player(),
                    body.message() == null ? "" : body.message());
            case "join", "quit" -> chatService.broadcast("[系统]",
                    (body.player() == null ? "?" : body.player())
                            + ("join".equals(body.type()) ? " 加入了服务器" : " 离开了服务器"));
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
