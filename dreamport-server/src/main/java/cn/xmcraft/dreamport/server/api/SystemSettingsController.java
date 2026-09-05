package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.settings.SystemSettingsService;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统设置 CRUD（管理员）：注册/AI评分/邀请/游戏/下载中心。
 * 全部存 dp_setting，热生效。
 */
@RestController
@RequestMapping("/api/admin/settings")
public class SystemSettingsController {

    private final SystemSettingsService settingsService;
    private final AuditService auditService;

    public SystemSettingsController(SystemSettingsService settingsService, AuditService auditService) {
        this.settingsService = settingsService;
        this.auditService = auditService;
    }

    private String op(HttpServletRequest request) {
        return AuthUtil.currentUser(request) == null ? "system" : AuthUtil.currentUser(request);
    }

    private ResponseEntity<Object> guard(HttpServletRequest request) {
        if (AuthUtil.currentUser(request) == null)
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        if (!AuthUtil.isAdmin(request))
            return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
        return null;
    }

    @GetMapping("/register")
    public ResponseEntity<Object> getRegister(HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        return ResponseEntity.ok(Map.of("success", true, "data", settingsService.registerConfig()));
    }

    @PutMapping("/register")
    public ResponseEntity<Object> saveRegister(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        settingsService.saveRegisterConfig(body);
        auditService.log("settings_register", op(request), "", body.toString());
        return ResponseEntity.ok(ApiResponse.success("注册设置已保存"));
    }

    @GetMapping("/llm")
    public ResponseEntity<Object> getLlm(HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        var config = settingsService.llmConfig();
        // 脱敏 API Key
        if (config.containsKey("apiKey") && config.get("apiKey") != null && !String.valueOf(config.get("apiKey")).isBlank()) {
            config.put("hasApiKey", true);
            config.put("apiKey", "***");
        } else {
            config.put("hasApiKey", false);
            config.put("apiKey", "");
        }
        return ResponseEntity.ok(Map.of("success", true, "data", config));
    }

    @PutMapping("/llm")
    public ResponseEntity<Object> saveLlm(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        // 如果 apiKey 是 *** 占位符，保留原值
        if ("***".equals(body.get("apiKey"))) {
            var old = settingsService.llmConfig();
            body.put("apiKey", old.getOrDefault("apiKey", ""));
        }
        settingsService.saveLlmConfig(body);
        auditService.log("settings_llm", op(request), "", "");
        return ResponseEntity.ok(ApiResponse.success("AI 评分设置已保存"));
    }

    @GetMapping("/invite")
    public ResponseEntity<Object> getInvite(HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        return ResponseEntity.ok(Map.of("success", true, "data", settingsService.inviteConfig()));
    }

    @PutMapping("/invite")
    public ResponseEntity<Object> saveInvite(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        settingsService.saveInviteConfig(body);
        auditService.log("settings_invite", op(request), "", body.toString());
        return ResponseEntity.ok(ApiResponse.success("邀请设置已保存"));
    }

    @GetMapping("/game")
    public ResponseEntity<Object> getGame(HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        return ResponseEntity.ok(Map.of("success", true, "data", settingsService.gameConfig()));
    }

    @PutMapping("/game")
    public ResponseEntity<Object> saveGame(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        settingsService.saveGameConfig(body);
        auditService.log("settings_game", op(request), "", body.toString());
        return ResponseEntity.ok(ApiResponse.success("游戏设置已保存"));
    }

    @GetMapping("/downloads")
    public ResponseEntity<Object> getDownloads(HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        return ResponseEntity.ok(Map.of("success", true, "data", settingsService.downloads()));
    }

    @PutMapping("/downloads")
    public ResponseEntity<Object> saveDownloads(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        settingsService.saveDownloads(body);
        auditService.log("settings_downloads", op(request), "", "");
        return ResponseEntity.ok(ApiResponse.success("下载中心已保存"));
    }
}
