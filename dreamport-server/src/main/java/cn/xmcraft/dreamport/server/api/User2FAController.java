package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.security.TokenService;
import cn.xmcraft.dreamport.server.security.User2FAService;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
 * 2FA 自助管理(需真实登录会话):状态/绑定/启用/停用。
 * 登录中间态的绑定/验证走 AuthController /login/2fa/*(challenge 凭据,非会话)。
 */
@RestController
@RequestMapping("/api/user/2fa")
public class User2FAController {

    private final User2FAService twoFaService;
    private final cn.xmcraft.dreamport.server.security.TotpService totpService;

    public User2FAController(User2FAService twoFaService,
                             cn.xmcraft.dreamport.server.security.TotpService totpService) {
        this.twoFaService = twoFaService;
        this.totpService = totpService;
    }

    @GetMapping("/status")
    public ResponseEntity<Object> status(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        return ResponseEntity.ok(ApiResponse.success(null, Map.of(
                "enabled", twoFaService.isEnabled(me),
                "pending", twoFaService.isPending(me))));
    }

    /** 开始绑定:@return secret(Base32)与 otpauth URI(前端渲染二维码) */
    @PostMapping("/setup")
    public ResponseEntity<Object> setup(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        if (twoFaService.isEnabled(me)) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("两步验证已启用,请先停用"));
        }
        String secret = twoFaService.startEnroll(me);
        String uri = totpService.otpauthUri(secret, me, "DreamPort");
        return ResponseEntity.ok(ApiResponse.success(null, Map.of("secret", secret, "otpauth", uri)));
    }

    public record EnableBody(String code) {
    }

    /** 验证码确认启用:@return 一次性明文恢复码 */
    @PostMapping("/enable")
    public ResponseEntity<Object> enable(@RequestBody EnableBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        List<String> recovery = twoFaService.enable(me, body.code() == null ? "" : body.code().trim());
        if (recovery.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("验证码错误,请确认验证器时间后重试"));
        }
        return ResponseEntity.ok(ApiResponse.success("两步验证已启用", Map.of("recoveryCodes", recovery)));
    }

    public record DisableBody(String password, String code) {
    }

    /** 停用:需要密码 + 验证码(TOTP 或恢复码) */
    @PostMapping("/disable")
    public ResponseEntity<Object> disable(@RequestBody DisableBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        boolean ok = twoFaService.disable(me, body.password() == null ? "" : body.password(),
                body.code() == null ? "" : body.code());
        if (!ok) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("密码或验证码错误"));
        }
        return ResponseEntity.ok(ApiResponse.success("两步验证已停用"));
    }
}
