package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.blessingskin.BlessingSkinService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.notification.NotificationRepository;
import cn.xmcraft.dreamport.server.settings.SystemSettingsService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
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
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final BlessingSkinService blessingSkinService;

    public SystemSettingsController(SystemSettingsService settingsService, AuditService auditService,
                                    UserRepository userRepository, NotificationRepository notificationRepository,
                                    BlessingSkinService blessingSkinService) {
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.blessingSkinService = blessingSkinService;
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

    @GetMapping("/questionnaire")
    public ResponseEntity<Object> getQuestionnaire(HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        return ResponseEntity.ok(Map.of("success", true, "data", settingsService.questionnaireConfig()));
    }

    @PutMapping("/questionnaire")
    public ResponseEntity<Object> saveQuestionnaire(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        settingsService.saveQuestionnaireConfig(body);
        auditService.log("settings_questionnaire", op(request), "", body.toString());
        return ResponseEntity.ok(ApiResponse.success("问卷设置已保存"));
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

    @GetMapping("/announcements")
    public ResponseEntity<Object> getAnnouncements(HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        return ResponseEntity.ok(Map.of("success", true, "data", settingsService.announcementsConfig()));
    }

    @PutMapping("/announcements")
    public ResponseEntity<Object> saveAnnouncements(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        // 检测新增资讯 → 给全站用户写站内通知(铃铛中心展示)
        var oldNews = settingsService.announcementsConfig().get("news");
        var oldIds = new java.util.HashSet<String>();
        if (oldNews instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("id") != null) oldIds.add(String.valueOf(m.get("id")));
            }
        }
        settingsService.saveAnnouncementsConfig(body);
        if (body.get("news") instanceof List<?> newNews) {
            for (Object o : newNews) {
                if (o instanceof Map<?, ?> m && m.get("id") != null && !oldIds.contains(String.valueOf(m.get("id")))) {
                    Object t = m.get("title");
                    String title = t == null ? "无标题" : String.valueOf(t);
                    for (UserRecord u : userRepository.listAll()) {
                        if ("banned".equals(u.status())) continue;
                        notificationRepository.save(new cn.xmcraft.dreamport.server.notification.NotificationRecord(
                                null, u.username(), "announcement", "新公告资讯", title, null, null, null));
                    }
                }
            }
        }
        auditService.log("settings_announcements", op(request), "", "");
        return ResponseEntity.ok(ApiResponse.success("公告内容已保存"));
    }

    @GetMapping("/astrbot")
    public ResponseEntity<Object> getAstrbot(HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        var config = settingsService.astrbotConfig();
        String token = String.valueOf(config.getOrDefault("apiToken", ""));
        if (!token.isBlank()) {
            config.put("hasToken", true);
            config.put("apiToken", "***");
        } else {
            config.put("hasToken", false);
            config.put("apiToken", "");
        }
        return ResponseEntity.ok(Map.of("success", true, "data", config));
    }

    @PutMapping("/astrbot")
    public ResponseEntity<Object> saveAstrbot(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        if ("***".equals(body.get("apiToken"))) {
            body.put("apiToken", settingsService.astrbotConfig().getOrDefault("apiToken", ""));
        }
        try {
            settingsService.saveAstrbotConfig(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
        auditService.log("settings_astrbot", op(request), "", "");
        return ResponseEntity.ok(ApiResponse.success("QQ 互通设置已保存"));
    }

    @GetMapping("/blessingskin")
    public ResponseEntity<Object> getBlessingskin(HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        var config = settingsService.blessingskinConfig();
        maskSecret(config, "clientSecret", "hasClientSecret");
        maskSecret(config, "apiSecret", "hasApiSecret");
        return ResponseEntity.ok(Map.of("success", true, "data", config));
    }

    @PutMapping("/blessingskin")
    public ResponseEntity<Object> saveBlessingskin(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        var g = guard(request); if (g != null) return g;
        if ("***".equals(body.get("clientSecret"))) {
            body.put("clientSecret", settingsService.blessingskinConfig().get("clientSecret"));
        }
        if ("***".equals(body.get("apiSecret"))) {
            body.put("apiSecret", settingsService.blessingskinConfig().get("apiSecret"));
        }
        settingsService.saveBlessingskinConfig(body);
        blessingSkinService.clearCache();
        auditService.log("settings_blessingskin", op(request), "", "");
        return ResponseEntity.ok(ApiResponse.success("BlessingSkin 互通设置已保存"));
    }

    private void maskSecret(Map<String, Object> config, String key, String flag) {
        String v = String.valueOf(config.getOrDefault(key, ""));
        if (!v.isBlank()) {
            config.put(flag, true);
            config.put(key, "***");
        } else {
            config.put(flag, false);
            config.put(key, "");
        }
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
