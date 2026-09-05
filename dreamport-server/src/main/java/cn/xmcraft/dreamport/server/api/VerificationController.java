package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.infra.MailService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.verification.CaptchaService;
import cn.xmcraft.dreamport.server.verification.MinecraftVerifyService;
import cn.xmcraft.dreamport.server.verification.PasswordResetRecord;
import cn.xmcraft.dreamport.server.verification.PasswordResetRepository;
import cn.xmcraft.dreamport.server.verification.VerifyCodeService;
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
 * 账户/验证端点（契约对齐旧版：captcha、verify/send、auth/forgot|reset、user/{status,profile,password,
 * update,email/update,avatar/upload}、user/{minecraft,bedrock}/*、verify/{check,status}）。
 */
@RestController
@RequestMapping("/api")
public class VerificationController {

    private final CaptchaService captchaService;
    private final VerifyCodeService verifyCodeService;
    private final MinecraftVerifyService minecraftVerifyService;
    private final PasswordResetRepository passwordResetRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final RateLimiter rateLimiter;
    private final SettingService settingService;
    private final cn.xmcraft.dreamport.server.security.PasswordService passwordService;
    private final cn.xmcraft.dreamport.server.config.WlProps props;

    public VerificationController(CaptchaService captchaService, VerifyCodeService verifyCodeService,
                                  MinecraftVerifyService minecraftVerifyService,
                                  PasswordResetRepository passwordResetRepository,
                                  UserRepository userRepository, MailService mailService,
                                  RateLimiter rateLimiter, SettingService settingService,
                                  cn.xmcraft.dreamport.server.security.PasswordService passwordService,
                                  cn.xmcraft.dreamport.server.config.WlProps props) {
        this.captchaService = captchaService;
        this.verifyCodeService = verifyCodeService;
        this.minecraftVerifyService = minecraftVerifyService;
        this.passwordResetRepository = passwordResetRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.rateLimiter = rateLimiter;
        this.settingService = settingService;
        this.passwordService = passwordService;
        this.props = props;
    }

    public record EmailBody(String email, String language) {
    }

    public record CaptchaBody(String captchaToken, String captchaAnswer) {
    }

    public record PasswordBody(String token, String password) {
    }

    public record ForgotBody(String email) {
    }

    public record MinecraftSetBody(String minecraftName) {
    }

    public record BedrockSetBody(String bedrockName) {
    }

    public record EmailUpdateBody(String email, String verifyCode) {
    }

    public record PasswordChangeBody(String oldPassword, String newPassword) {
    }

    public record AvatarBody(String image) {
    }

    // ---------- 图形验证码 / 邮箱验证码 ----------

    @PostMapping({"/captcha/generate", "/captcha"})
    public Map<String, Object> generateCaptcha() {
        CaptchaService.CaptchaImage image = captchaService.generate();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("captchaToken", image.token());
        body.put("image", "data:image/png;base64," + image.imageBase64());
        body.put("expiresIn", 300);
        return body;
    }

    @PostMapping("/verify/send")
    public ResponseEntity<Map<String, Object>> sendVerifyCode(@RequestBody EmailBody body,
                                                              HttpServletRequest request) {
        if (!rateLimiter.allow("verify-code:" + clientIp(request), 3, 300_000)) {
            return rateLimited();
        }
        if (body.email() == null || !body.email().contains("@")) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("邮箱格式不正确"));
        }
        verifyCodeService.send(body.email(), body.language());
        return ResponseEntity.ok(ApiResponse.success("验证码已发送，请查收邮箱"));
    }

    // ---------- 密码重置 ----------

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody ForgotBody body) {
        String email = body.email() == null ? "" : body.email();
        // 防枚举：无论是否存在都返回成功文案
        var userOpt = userRepository.listAll().stream()
                .filter(u -> email.equalsIgnoreCase(u.email()))
                .findFirst();
        if (userOpt.isPresent() && userOpt.get().email() != null) {
            PasswordResetRecord token = new PasswordResetRecord(null, userOpt.get().username(),
                    PasswordResetRecord.generateToken(), null, null, null);
            passwordResetRepository.save(token);
            mailService.sendPasswordReset(userOpt.get().username(), userOpt.get().email(),
                    props.webRegisterUrl() + "/reset-password?token=" + token.token(), "zh");
        }
        return ResponseEntity.ok(ApiResponse.success("如果该邮箱已注册，重置链接已发送"));
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody PasswordBody body) {
        if (body.password() == null || body.password().length() < 8) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("新密码至少 8 位"));
        }
        var record = body.token() == null ? null : passwordResetRepository.findByToken(body.token());
        if (record == null || record.isEmpty() || !record.get().valid(System.currentTimeMillis())) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("重置链接无效或已过期"));
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(record.get().username());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("用户不存在"));
        }
        UserRecord user = userOpt.get();
        userRepository.save(new UserRecord(user.id(), user.username(), user.email(), user.status(),
                "bcrypt", hashFor(user, body.password()), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), user.banReason(), user.banTime(), user.avatar()));
        passwordResetRepository.save(record.get().markUsed());
        return ResponseEntity.ok(ApiResponse.success("密码已重置，请使用新密码登录"));
    }

    private String hashFor(UserRecord user, String rawPassword) {
        return passwordService.hash(rawPassword);
    }

    private String emailToUsernameGuess(String email) {
        return email == null ? "" : email.split("@")[0];
    }

    // ---------- ID 绑定与验证 ----------

    @PostMapping("/user/minecraft/set")
    public ResponseEntity<Map<String, Object>> setMinecraft(@RequestBody MinecraftSetBody body,
                                                            HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        var result = minecraftVerifyService.setMinecraftId(me, body.minecraftName());
        return wrap(result);
    }

    @PostMapping("/user/minecraft/verify")
    public ResponseEntity<Map<String, Object>> verifyMinecraft(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        return wrap(minecraftVerifyService.verifyMinecraft(me));
    }

    @GetMapping("/user/minecraft/status")
    public ResponseEntity<Map<String, Object>> minecraftStatus(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(me);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("minecraftName", userOpt.map(UserRecord::minecraftName).orElse(null));
        data.put("verified", userOpt.map(u -> u.minecraftUuid() != null).orElse(false));
        data.put("status", userOpt.map(UserRecord::status).orElse(null));
        return ResponseEntity.ok(ApiResponse.success(null, data));
    }

    @PostMapping("/user/bedrock/set")
    public ResponseEntity<Map<String, Object>> setBedrock(@RequestBody BedrockSetBody body,
                                                          HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        return wrap(minecraftVerifyService.setBedrockId(me, body.bedrockName(), "."));
    }

    @PostMapping("/user/bedrock/verify")
    public ResponseEntity<Map<String, Object>> verifyBedrock(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        return wrap(minecraftVerifyService.verifyBedrock(me));
    }

    @PostMapping("/user/bedrock/cancel")
    public ResponseEntity<Map<String, Object>> cancelBedrock(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        return wrap(minecraftVerifyService.setBedrockId(me, "", "."));
    }

    @GetMapping("/user/bedrock/status")
    public ResponseEntity<Map<String, Object>> bedrockStatus(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(me);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bedrockName", userOpt.map(UserRecord::bedrockName).orElse(null));
        data.put("verified", userOpt.map(UserRecord::bedrockVerified).orElse(false));
        return ResponseEntity.ok(ApiResponse.success(null, data));
    }

    @PostMapping("/verify/check")
    public ResponseEntity<Map<String, Object>> verifyCheck(HttpServletRequest request) {
        return verifyMinecraft(request);
    }

    @GetMapping("/verify/status")
    public ResponseEntity<Map<String, Object>> verifyStatus(HttpServletRequest request) {
        return minecraftStatus(request);
    }

    // ---------- 资料 ----------

    @GetMapping("/user/status")
    public ResponseEntity<Map<String, Object>> userStatus(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(me);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", me);
        data.put("email", userOpt.map(UserRecord::email).orElse(null));
        data.put("status", userOpt.map(UserRecord::status).orElse(null));
        return ResponseEntity.ok(ApiResponse.success(null, data));
    }

    @GetMapping("/user/profile")
    public ResponseEntity<Map<String, Object>> profile(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(me);
        if (userOpt.isEmpty()) {
            return unauthorized();
        }
        UserRecord u = userOpt.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", u.username());
        data.put("email", u.email());
        data.put("status", u.status());
        data.put("regTime", u.regTime());
        data.put("minecraftName", u.minecraftName());
        data.put("minecraftUuid", u.minecraftUuid());
        data.put("bedrockName", u.bedrockName());
        data.put("avatar", u.avatar());
        data.put("questionnaireScore", u.questionnaireScore());
        data.put("questionnairePassed", u.questionnairePassed());
        data.put("invitedBy", u.invitedBy());
        return ResponseEntity.ok(ApiResponse.success(null, data));
    }

    @PostMapping("/user/password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody PasswordChangeBody body,
                                                              HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(me);
        if (userOpt.isEmpty()) {
            return unauthorized();
        }
        UserRecord user = userOpt.get();
        var verify = passwordService.verify(body.oldPassword(), user.passwordAlgo(), user.passwordHash());
        if (verify == cn.xmcraft.dreamport.server.security.PasswordService.VerifyResult.FAIL) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("旧密码错误"));
        }
        if (body.newPassword() == null || body.newPassword().length() < 8) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("新密码至少 8 位"));
        }
        userRepository.save(withPassword(user, "bcrypt", passwordService.hash(body.newPassword())));
        return ResponseEntity.ok(ApiResponse.success("密码已修改"));
    }

    @PostMapping("/user/email/update")
    public ResponseEntity<Map<String, Object>> updateEmail(@RequestBody EmailUpdateBody body,
                                                           HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        if (!verifyCodeService.check(body.email(), body.verifyCode())) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("邮箱验证码错误或已过期"));
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(me);
        if (userOpt.isEmpty()) {
            return unauthorized();
        }
        UserRecord user = userOpt.get();
        userRepository.save(new UserRecord(user.id(), user.username(), body.email(), user.status(),
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), user.banReason(), user.banTime(), user.avatar()));
        return ResponseEntity.ok(ApiResponse.success("邮箱已更新"));
    }

    @PostMapping("/user/update")
    public ResponseEntity<Map<String, Object>> updateUser(@RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        return updateEmail(new EmailUpdateBody((String) body.get("email"), (String) body.get("verifyCode")),
                request);
    }

    @PostMapping("/user/avatar/upload")
    public ResponseEntity<Map<String, Object>> uploadAvatar(@RequestBody AvatarBody body,
                                                            HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        String image = body.image();
        if (image == null || image.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("图片为空"));
        }
        if (image.startsWith("data:")) {
            image = image.substring(image.indexOf(',') + 1);
        }
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(image);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("图片解码失败"));
        }
        if (bytes.length > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("头像不能超过 2MB"));
        }
        String filename = "avatar_" + me + "_" + Long.toHexString(System.currentTimeMillis()) + ".png";
        java.nio.file.Path dir = java.nio.file.Path.of("static", "uploads");
        try {
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.write(dir.resolve(filename), bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.failure("头像保存失败"));
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(me);
        if (userOpt.isEmpty()) {
            return unauthorized();
        }
        UserRecord user = userOpt.get();
        userRepository.save(new UserRecord(user.id(), user.username(), user.email(), user.status(),
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), user.banReason(), user.banTime(),
                "/uploads/" + filename));
        return ResponseEntity.ok(ApiResponse.success("头像已更新",
                Map.of("avatar", "/uploads/" + filename)));
    }

    // ---------- 工具 ----------

    private UserRecord withPassword(UserRecord user, String algo, String hash) {
        return new UserRecord(user.id(), user.username(), user.email(), user.status(), algo, hash,
                user.regTime(), user.discordId(), user.qqNumber(), user.qqBoundAt(),
                user.questionnaireScore(), user.questionnairePassed(), user.questionnaireReviewSummary(),
                user.questionnaireScoredAt(), user.questionnaireReasons(), user.questionnaireAnswers(),
                user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(), user.verifiedAt(),
                user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), user.banReason(), user.banTime(), user.avatar());
    }

    private ResponseEntity<Map<String, Object>> wrap(MinecraftVerifyService.Result result) {
        return result.success()
                ? ResponseEntity.ok(ApiResponse.success(result.message()))
                : ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.failure("未登录"));
    }

    private ResponseEntity<Map<String, Object>> rateLimited() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.failure("请求过于频繁，请稍后再试"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    @SuppressWarnings("unused")
    private SettingService settingService() {
        return settingService;
    }
}
