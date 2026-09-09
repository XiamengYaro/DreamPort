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
import java.util.List;
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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final cn.xmcraft.dreamport.server.security.User2FAService twoFaService;
    private final cn.xmcraft.dreamport.server.security.TotpService totpService;
    private final cn.xmcraft.dreamport.server.user.UserRepository userRepository;

    public AuthController(UserService userService, TokenService tokenService, RateLimiter rateLimiter,
                          cn.xmcraft.dreamport.server.settings.SettingService settingService,
                          cn.xmcraft.dreamport.server.verification.CaptchaService captchaService,
                          cn.xmcraft.dreamport.server.verification.VerifyCodeService verifyCodeService,
                          cn.xmcraft.dreamport.server.invite.InviteService inviteService,
                          cn.xmcraft.dreamport.server.settings.SystemSettingsService systemSettings,
                          org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                          cn.xmcraft.dreamport.server.security.User2FAService twoFaService,
                          cn.xmcraft.dreamport.server.security.TotpService totpService,
                          cn.xmcraft.dreamport.server.user.UserRepository userRepository) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.settingService = settingService;
        this.captchaService = captchaService;
        this.verifyCodeService = verifyCodeService;
        this.inviteService = inviteService;
        this.systemSettings = systemSettings;
        this.jdbcTemplate = jdbcTemplate;
        this.twoFaService = twoFaService;
        this.totpService = totpService;
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
                                  Boolean rulesAccepted) {
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req,
                                                        HttpServletRequest request) {
        if (!rateLimiter.allow("register:" + clientIp(request), 3, 60_000)) {
            return tooManyRequests();
        }
        // 守则门:注册页强制阅读并勾选同意,否则拒绝注册(同意记录随注册写入 dp_rules_consent)
        if (req.rulesAccepted() == null || !req.rulesAccepted()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("请先阅读并同意服务器守则"));
        }
        // 图形验证码：仅当前端携带 token 时才校验（旧注册页无图形验证码控件）
        if (req.captchaToken() != null && !req.captchaToken().isBlank()
                && !captchaService.check(req.captchaToken(), req.captchaAnswer())) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("图形验证码错误或已过期"));
        }
        // Java 游戏名收紧为正版规则(official:字母数字下划线)
        String username = req.username();
        if (username != null && !username.isBlank() && !username.matches("^[A-Za-z0-9_]{3,16}$")) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("游戏名不合法（3-16 位字母数字下划线）"));
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
        // 注册即同意守则(守则门在注册页强制;记录供 status/审计查询)
        jdbcTemplate.update(
                "INSERT IGNORE INTO dp_rules_consent (username, accepted_at) VALUES (?, ?)",
                result.user().username(), System.currentTimeMillis());
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
        // 2FA 拦截:已启用 → needs_2fa;管理员强制开关开启且管理员未绑定 → needs_2fa_setup
        String twoFaGate = twoFaGate(u.username(), admin);
        if (twoFaGate != null) {
            Map<String, Object> gate = new LinkedHashMap<>();
            gate.put("status", twoFaGate);
            gate.put("username", u.username());
            gate.put("isAdmin", admin);
            gate.put("challengeId", twoFaService.createChallenge(u.username(), admin, "needs_2fa_setup".equals(twoFaGate)));
            return ResponseEntity.ok(ApiResponse.success(null, gate));
        }
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
        // 修复审计 C1：管理员登录必须命中 admins 名单，否则任意已通过玩家即可提权为 admin
        if (!inAdminsList(user.get().username())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("该账号不在管理员名单内"));
        }
        // 2FA 拦截(同 /login):已启用验证 / 强制绑定
        boolean admin = true;
        String twoFaGate = twoFaGate(user.get().username(), admin);
        if (twoFaGate != null) {
            Map<String, Object> gate = new LinkedHashMap<>();
            gate.put("status", twoFaGate);
            gate.put("username", user.get().username());
            gate.put("isAdmin", true);
            gate.put("challengeId", twoFaService.createChallenge(user.get().username(), admin,
                    "needs_2fa_setup".equals(twoFaGate)));
            return ResponseEntity.ok(ApiResponse.success(null, gate));
        }
        String token = tokenService.issue(user.get().username(), TokenService.ROLE_ADMIN);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", user.get().username());
        data.put("isAdmin", true);
        return ResponseEntity.ok(ApiResponse.success("登录成功", data));
    }

    // ---------- 2FA 登录中间态(challenge 凭据非会话,不授予任何用户端点权限) ----------

    /** 需要进入 2FA 中间态?返回状态名,否则 null */
    private String twoFaGate(String username, boolean admin) {
        if (twoFaService.isEnabled(username)) {
            return "needs_2fa";
        }
        if (admin && admin2faRequired() && !twoFaService.isEnabled(username)
                && !twoFaService.isPending(username)) {
            return "needs_2fa_setup";
        }
        return null;
    }

    private boolean admin2faRequired() {
        var cfg = settingService.get(
                cn.xmcraft.dreamport.server.settings.SettingService.KEY_SECURITY_CONFIG, java.util.Map.class);
        Object v = cfg == null ? null : cfg.get("admin2faRequired");
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    public record TwoFaVerifyRequest(String challengeId, String code) {
    }

    /** 登录两步验证:TOTP / 恢复码 / 邮箱备用码 */
    @PostMapping("/login/2fa")
    public ResponseEntity<Map<String, Object>> login2fa(@RequestBody TwoFaVerifyRequest req,
                                                        HttpServletRequest request) {
        if (!rateLimiter.allow("login-2fa:" + clientIp(request), 10, 60_000)) {
            return tooManyRequests();
        }
        var challenge = twoFaService.consumeChallenge(req.challengeId());
        if (challenge == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("登录会话已过期,请重新登录"));
        }
        boolean ok;
        try {
            ok = twoFaService.verifyLogin(challenge.username(), req.code());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.failure(e.getMessage()));
        }
        if (!ok) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("验证码错误"));
        }
        twoFaService.removeChallenge(req.challengeId());
        return ResponseEntity.ok(ApiResponse.success("登录成功", completeLogin(challenge)));
    }

    /** 邮箱备用通道:发码(3 次/5 分钟) */
    @PostMapping("/login/2fa/email")
    public ResponseEntity<Map<String, Object>> login2faEmail(@RequestBody TwoFaVerifyRequest req,
                                                             HttpServletRequest request) {
        var challenge = twoFaService.consumeChallenge(req == null ? null : req.challengeId());
        if (challenge == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("登录会话已过期,请重新登录"));
        }
        boolean sent = twoFaService.sendEmailCode(challenge.username());
        if (!sent) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("发送过于频繁或账号未绑定邮箱"));
        }
        return ResponseEntity.ok(ApiResponse.success("验证码已发送到绑定邮箱"));
    }

    /** 管理员强制绑定:开始(返回二维码信息) */
    @PostMapping("/login/2fa/setup")
    public ResponseEntity<Map<String, Object>> login2faSetup(@RequestBody TwoFaVerifyRequest req) {
        var challenge = twoFaService.consumeChallenge(req == null ? null : req.challengeId());
        if (challenge == null || !challenge.forcedSetup()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("登录会话无效"));
        }
        String secret = twoFaService.startEnroll(challenge.username());
        String uri = totpService.otpauthUri(secret, challenge.username(), "DreamPort");
        return ResponseEntity.ok(ApiResponse.success(null,
                Map.of("secret", secret, "otpauth", uri)));
    }

    /** 管理员强制绑定:验证码确认并完成登录,@return 一次性恢复码 */
    @PostMapping("/login/2fa/enable")
    public ResponseEntity<Map<String, Object>> login2faEnable(@RequestBody TwoFaVerifyRequest req) {
        var challenge = twoFaService.consumeChallenge(req == null ? null : req.challengeId());
        if (challenge == null || !challenge.forcedSetup()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("登录会话无效"));
        }
        List<String> recovery = twoFaService.enable(challenge.username(),
                req.code() == null ? "" : req.code().trim());
        if (recovery.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("验证码错误,请确认验证器时间后重试"));
        }
        twoFaService.removeChallenge(req.challengeId());
        Map<String, Object> data = new LinkedHashMap<>(completeLogin(challenge));
        data.put("recoveryCodes", recovery);
        return ResponseEntity.ok(ApiResponse.success("两步验证已启用,登录成功", data));
    }

    private Map<String, Object> completeLogin(cn.xmcraft.dreamport.server.security.User2FAService.Challenge challenge) {
        var userOpt = userRepository.findByUsernameIgnoreCase(challenge.username());
        var u = userOpt.orElseThrow();
        String token = tokenService.issue(u.username(),
                challenge.admin() ? TokenService.ROLE_ADMIN : TokenService.ROLE_USER);
        String status = "pending".equals(u.status())
                && Boolean.TRUE.equals(systemSettings.questionnaireConfig().getOrDefault("enabled", true))
                && u.questionnaireScore() == 0
                ? "needs_questionnaire" : u.status();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", u.username());
        data.put("status", status);
        data.put("isAdmin", challenge.admin());
        return data;
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
        return cn.xmcraft.dreamport.server.web.ClientIp.realIp(request);
    }
}
