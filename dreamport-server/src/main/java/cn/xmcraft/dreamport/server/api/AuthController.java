package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.security.TokenService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserService;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import cn.xmcraft.dreamport.server.web.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账户端点（契约对齐旧版 /api/register、/api/login、/api/auth/validate、/api/admin/login）。
 * 邮箱验证码/图形验证码/问卷联动在 P3 接入；P1 骨架先打通账号主链路。
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;
    private final RateLimiter rateLimiter;
    private final cn.xmcraft.dreamport.server.settings.SettingService settingService;

    public AuthController(UserService userService, TokenService tokenService, RateLimiter rateLimiter,
                          cn.xmcraft.dreamport.server.settings.SettingService settingService) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.settingService = settingService;
    }

    /** 是否在管理员名单（dp_setting admins.list，语义对齐旧版 config.admins） */
    private boolean inAdminsList(String username) {
        java.util.List<Object> admins = settingService.get(
                cn.xmcraft.dreamport.server.settings.SettingService.KEY_ADMINS, java.util.List.class);
        return admins != null && admins.stream()
                .anyMatch(a -> String.valueOf(a).equalsIgnoreCase(username));
    }

    public record RegisterRequest(String username, String email, String password) {
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req,
                                                        HttpServletRequest request) {
        if (!rateLimiter.allow("register:" + clientIp(request), 3, 60_000)) {
            return tooManyRequests();
        }
        var result = userService.register(req.username(), req.email(), req.password());
        if (!result.ok()) {
            String message = switch (result.error()) {
                case INVALID_USERNAME -> "用户名不合法（3-16 位字母数字_-）";
                case INVALID_EMAIL -> "邮箱格式不正确";
                case INVALID_PASSWORD -> "密码至少 6 位";
                case USERNAME_TAKEN -> "用户名已被注册";
                case EMAIL_TAKEN -> "该邮箱已注册账号";
            };
            return ResponseEntity.badRequest().body(ApiResponse.failure(message));
        }
        // P3：接入邮箱验证码/问卷后，状态流转按旧版分支；骨架先发 token
        String token = tokenService.issue(result.user().username(), TokenService.ROLE_USER);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", result.user().username());
        data.put("status", result.user().status());
        return ResponseEntity.ok(ApiResponse.success("注册成功", data));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req,
                                                     HttpServletRequest request) {
        if (!rateLimiter.allow("login:" + clientIp(request), 5, 60_000)) {
            return tooManyRequests();
        }
        var user = userService.authenticate(req.username(), req.password());
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("用户名或密码错误"));
        }
        UserRecord u = user.get();
        if (u.banned()) {
            String reason = u.banReason() == null || u.banReason().isBlank()
                    ? "账户已被封禁" : "账户已被封禁：" + u.banReason();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(reason));
        }
        // 语义对齐旧版：admins 名单内玩家普通登录即管理员（否则无法进入后台）
        boolean admin = inAdminsList(u.username());
        String token = tokenService.issue(u.username(),
                admin ? TokenService.ROLE_ADMIN : TokenService.ROLE_USER);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", u.username());
        data.put("status", u.status());
        data.put("isAdmin", admin);
        return ResponseEntity.ok(ApiResponse.success("登录成功", data));
    }

    @PostMapping("/admin/login")
    public ResponseEntity<Map<String, Object>> adminLogin(@RequestBody LoginRequest req,
                                                          HttpServletRequest request) {
        if (!rateLimiter.allow("admin-login:" + clientIp(request), 5, 60_000)) {
            return tooManyRequests();
        }
        var user = userService.authenticate(req.username(), req.password());
        if (user.isEmpty() || !user.get().approved()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("用户名或密码错误"));
        }
        // P4：接入 ops.json/管理员名单双重校验（对齐旧版 OpsManager.isOp + admins 列表）
        String token = tokenService.issue(user.get().username(), TokenService.ROLE_ADMIN);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", user.get().username());
        data.put("isAdmin", true);
        return ResponseEntity.ok(ApiResponse.success("登录成功", data));
    }

    @GetMapping("/auth/validate")
    public Map<String, Object> validate(HttpServletRequest request) {
        String username = AuthUtil.currentUser(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", username != null);
        data.put("username", username);
        data.put("isAdmin", AuthUtil.isAdmin(request));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }

    private ResponseEntity<Map<String, Object>> tooManyRequests() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.failure("请求过于频繁，请稍后再试"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
