package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.common.HeartbeatRequest;
import cn.xmcraft.dreamport.common.HeartbeatResponse;
import cn.xmcraft.dreamport.common.LoginCheckRequest;
import cn.xmcraft.dreamport.common.Protocol;
import cn.xmcraft.dreamport.server.config.WlProps;
import cn.xmcraft.dreamport.server.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器间内部端点（鉴权：X-Server-Id/X-Server-Token，Rules.md §5）。
 * P1 提供 login-check 与 heartbeat；login-record/events/economy/commands 在 P5 接入。
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    private final UserService userService;
    private final WlProps props;
    /** 各服务器最近一次心跳（内存态；P4 落 dp_server） */
    private final Map<String, HeartbeatRequest> lastHeartbeats = new ConcurrentHashMap<>();

    public InternalController(UserService userService, WlProps props) {
        this.userService = userService;
        this.props = props;
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
        lastHeartbeats.put(req.serverId(), req);
        return ResponseEntity.ok(new HeartbeatResponse(true, System.currentTimeMillis()));
    }

    /** 最近心跳（运维观察用，后续随 dp_server 落库移除） */
    public Map<String, HeartbeatRequest> lastHeartbeats() {
        return Map.copyOf(lastHeartbeats);
    }

    private ResponseEntity<Object> requireServerToken(HttpServletRequest request) {
        String token = request.getHeader(Protocol.HEADER_SERVER_TOKEN);
        if (token == null || !props.internal().serverToken().equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", cn.xmcraft.dreamport.common.ErrorCode.TOKEN_MISSING.code(),
                            "message", "服务器认证失败"));
        }
        return null;
    }

    private ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", 2001, "message", message));
    }
}
