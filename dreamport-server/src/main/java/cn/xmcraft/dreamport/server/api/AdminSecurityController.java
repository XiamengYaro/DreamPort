package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账号安全配置(后台「系统设置」):管理员强制 2FA 开关等。
 */
@RestController
@RequestMapping("/api/admin/security")
public class AdminSecurityController {

    private final SettingService settingService;
    private final AuditService auditService;

    public AdminSecurityController(SettingService settingService, AuditService auditService) {
        this.settingService = settingService;
        this.auditService = auditService;
    }

    @GetMapping("/config")
    public ResponseEntity<Object> getConfig(HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> cfg = settingService.getMap(SettingService.KEY_SECURITY_CONFIG);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("admin2faRequired", Boolean.TRUE.equals(cfg.get("admin2faRequired"))
                || "true".equalsIgnoreCase(String.valueOf(cfg.get("admin2faRequired"))));
        data.put("tokenMode", cfg.getOrDefault("tokenMode", "shared"));
        return ResponseEntity.ok(ApiResponse.success(null, data));
    }

    @PutMapping("/config")
    public ResponseEntity<Object> saveConfig(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> cfg = new LinkedHashMap<>(settingService.getMap(SettingService.KEY_SECURITY_CONFIG));
        boolean required = Boolean.TRUE.equals(body.get("admin2faRequired"));
        cfg.put("admin2faRequired", required);
        settingService.set(SettingService.KEY_SECURITY_CONFIG, cfg);
        auditService.log("settings_security", me, null, "管理员强制 2FA = " + required);
        return ResponseEntity.ok(ApiResponse.success("安全设置已保存"));
    }

    private static boolean admin(HttpServletRequest request) {
        return AuthUtil.currentUser(request) != null && AuthUtil.isAdmin(request);
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
    }
}
