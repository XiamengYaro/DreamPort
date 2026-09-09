package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.stats.ServerStatsService;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务器注册表管理（后台「服务器管理」Tab）：
 * 分服状态列表、按服令牌签发/轮换（dp_server.token_hash，per_server 模式）、启停。
 */
@RestController
@RequestMapping("/api/admin/servers")
public class AdminServerController {

    private final ServerStatsService statsService;
    private final SettingService settingService;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public AdminServerController(ServerStatsService statsService, SettingService settingService,
                                 AuditService auditService) {
        this.statsService = statsService;
        this.settingService = settingService;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<Object> list(HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", tokenMode());
        data.put("servers", statsService.adminServers());
        return ResponseEntity.ok(ApiResponse.success(null, data));
    }

    /** 签发/轮换按服令牌:明文仅本次响应返回一次,库中只存 SHA-256 */
    @PostMapping("/{serverId}/issue-token")
    public ResponseEntity<Object> issueToken(@PathVariable String serverId, HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        if (serverId == null || serverId.isBlank() || serverId.length() > 64) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "serverId 非法"));
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        statsService.setServerToken(serverId, sha256Hex(token));
        String operator = AuthUtil.currentUser(request);
        auditService.log("server_token_issue", operator, serverId, "签发/轮换按服令牌");
        return ResponseEntity.ok(ApiResponse.success("令牌已生成,仅本次显示,请立即粘贴到该服插件 config.yml",
                Map.of("serverId", serverId, "token", token)));
    }

    @PutMapping("/{serverId}/enabled")
    public ResponseEntity<Object> setEnabled(@PathVariable String serverId,
                                             @RequestBody Map<String, Object> body,
                                             HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("enabled")));
        statsService.setServerEnabled(serverId, enabled);
        String operator = AuthUtil.currentUser(request);
        auditService.log(enabled ? "server_enable" : "server_disable", operator, serverId,
                enabled ? "启用该服的 internal 通道" : "停用该服的 internal 通道(per_server 模式生效)");
        return ResponseEntity.ok(ApiResponse.success(enabled ? "已启用" : "已停用"));
    }

    @GetMapping("/token-mode")
    public ResponseEntity<Object> getTokenMode(HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        return ResponseEntity.ok(ApiResponse.success(null, Map.of("mode", tokenMode())));
    }

    @PutMapping("/token-mode")
    public ResponseEntity<Object> setTokenMode(@RequestBody Map<String, Object> body,
                                               HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        String mode = String.valueOf(body.get("mode"));
        if (!"shared".equals(mode) && !"per_server".equals(mode)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "mode 仅支持 shared|per_server"));
        }
        Map<String, Object> cfg = new LinkedHashMap<>(settingService.getMap(SettingService.KEY_SECURITY_CONFIG));
        String previous = String.valueOf(cfg.getOrDefault("tokenMode", "shared"));
        cfg.put("tokenMode", mode);
        settingService.set(SettingService.KEY_SECURITY_CONFIG, cfg);
        if (!previous.equals(mode)) {
            auditService.log("server_token_mode", AuthUtil.currentUser(request), mode,
                    "internal 鉴权模式 " + previous + " → " + mode);
        }
        return ResponseEntity.ok(ApiResponse.success("鉴权模式已保存"));
    }

    private String tokenMode() {
        Map<String, Object> cfg = settingService.getMap(SettingService.KEY_SECURITY_CONFIG);
        Object mode = cfg.get("tokenMode");
        return mode == null ? "shared" : String.valueOf(mode);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static boolean admin(HttpServletRequest request) {
        return AuthUtil.currentUser(request) != null && AuthUtil.isAdmin(request);
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(Map.of("success", false, "message", "需要管理员权限"));
    }
}
