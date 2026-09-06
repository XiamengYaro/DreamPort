package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.security.TokenService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
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
    private final cn.xmcraft.dreamport.server.verification.CaptchaService captchaService;
    private final cn.xmcraft.dreamport.server.verification.VerifyCodeService verifyCodeService;
    private final cn.xmcraft.dreamport.server.invite.InviteService inviteService;
    private final cn.xmcraft.dreamport.server.settings.SystemSettingsService systemSettings;
    private final UserRepository userRepository;

    public AuthController(UserService userService, TokenService tokenService, RateLimiter rateLimiter,
                          cn.xmcraft.dreamport.server.settings.SettingService settingService,
                          cn.xmcraft.dreamport.server.verification.CaptchaService captchaService,
                          cn.xmcraft.dreamport.server.verification.VerifyCodeService verifyCodeService,
                          cn.xmcraft.dreamport.server.invite.InviteService inviteService,
                          cn.xmcraft.dreamport.server.settings.SystemSettingsService systemSettings,
                          cn.xmcraft.dreamport.server.user.UserRepository userRepository) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.settingService = settingService;
        this.captchaService = captchaService;
        this.verifyCodeService = verifyCodeService;
        this.inviteService = inviteService;
        this.systemSettings = systemSettings;
        this.userRepository = userRepository;
    }

    /** 是否在管理员名单（dp_setting admins.list，语义对齐旧版 config.admins） */
    private boolean inAdminsList(String username) {
        java.util.List<Object> admins = settingService.get(
                cn.xmcraft.dreamport.server.settings.SettingService.KEY_ADMINS, java.util.List.class);
        return admins != null && admins.stream()
                .anyMatch(a -> String.valueOf(a).equalsIgnoreCase(username));
    }

    public record RegisterRequest(@com.fasterxml.jackson.annotation.JsonAlias({"minecraftName", "minecraft_name"}) String username,
                                  String email, String password,
                                  String verifyCode,
                                  @com.fasterxml.jackson.annotation.JsonAlias("captchaToken") String captchaToken,
                                  String captchaAnswer,
                                  String inviteCode,
                                  @com.fasterxml.jackson.annotation.JsonAlias("bedrock_name") String bedrockName) {
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req,
                                                        HttpServletRequest request) {
        if (!rateLimiter.allow("register:" + clientIp(request), 3, 60_000)) {
            return tooManyRequests();
        }
        // 图形验证码：仅当前端携带 token 时才校验（旧注册页无图形验证码控件）
        if (req.captchaToken() != null && !req.captchaToken().isBlank()
                && !captchaService.check(req.captchaToken(), req.captchaAnswer())) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("图形验证码错误或已过期"));
        }
        // 基岩-only 注册：用户名回退为基岩名（剥离 Geyser 前缀）
        String username = req.username();
        if ((username == null || username.isBlank()) && req.bedrockName() != null && !req.bedrockName().isBlank()) {
            username = req.bedrockName().startsWith(".") ? req.bedrockName().substring(1) : req.bedrockName();
        }
        // 邮箱验证码（邀请码注册豁免；开关来自管理面板 dp_setting，热生效）
        boolean hasInvite = req.inviteCode() != null && !req.inviteCode().isBlank();
        var regCfg = systemSettings.registerConfig();
        boolean requireCode = Boolean.TRUE.equals(regCfg.getOrDefault("requireEmailCode", true));
        if (requireCode && !hasInvite
                && !verifyCodeService.check(req.email(), req.verifyCode())) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("邮箱验证码错误或已过期"));
        }
        var result = userService.register(username, req.email(), req.password());
        if (!result.ok()) {
            String message = switch (result.error()) {
                case INVALID_USERNAME -> "用户名不合法（3-16 位字母数字_-）";
                case INVALID_EMAIL -> "邮箱格式不正确";
                case INVALID_PASSWORD -> "密码至少 6 位";
                case USERNAME_TAKEN -> "用户名已被注册";
                case EMAIL_TAKEN -> "该邮箱已注册账号";
                case EMAIL_DOMAIN_DENIED -> "该邮箱域名不在白名单内";
                case EMAIL_LIMIT -> "该邮箱注册账号数已达上限";
            };
            return ResponseEntity.badRequest().body(ApiResponse.failure(message));
        }
        // 邀请码注册路径
        if (hasInvite) {
            var inviteResult = inviteService.registerWithInvite(result.user().username(), req.inviteCode());
            if (!inviteResult.success()) {
                return ResponseEntity.badRequest().body(ApiResponse.failure(inviteResult.message()));
            }
        }
        // 基岩 ID 一并落库（未验证态，玩家后续可走基岩验证）
        if (req.bedrockName() != null && !req.bedrockName().isBlank()) {
            String bn = req.bedrockName().startsWith(".") ? req.bedrockName() : "." + req.bedrockName();
            var userOpt = userRepository.findByUsernameIgnoreCase(result.user().username());
            userOpt.ifPresent(u -> userRepository.save(new UserRecord(u.id(), u.username(), u.email(), u.status(),
                    u.passwordAlgo(), u.passwordHash(), u.regTime(), u.discordId(), u.qqNumber(), u.qqBoundAt(),
                    u.questionnaireScore(), u.questionnairePassed(), u.questionnaireReviewSummary(),
                    u.questionnaireScoredAt(), u.questionnaireReasons(), u.questionnaireAnswers(),
                    u.minecraftUuid(), u.minecraftName(), u.microsoftVerified(), u.verifiedAt(), u.verifyType(),
                    u.invitedBy(), null, bn, false, null, u.banReason(), u.banTime(), u.banUntil(), u.avatar())));
        }
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
        // 问卷未答（pending 且 0 分）→ 前端引导去答题
        String status = "pending".equals(u.status())
                && Boolean.TRUE.equals(systemSettings.questionnaireConfig().getOrDefault("enabled", true))
                && u.questionnaireScore() == 0
                ? "needs_questionnaire" : u.status();
        data.put("status", status);
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
