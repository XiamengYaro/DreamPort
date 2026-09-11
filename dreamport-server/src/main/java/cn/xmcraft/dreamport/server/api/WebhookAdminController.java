package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import cn.xmcraft.dreamport.server.webhook.WebhookDispatcherService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Webhook 配置与测试(后台「系统设置 → Webhook」):
 * urls[{url, secret}] + events 过滤(空=全部);测试端点发送 event=webhook.test。
 */
@RestController
@RequestMapping("/api/admin/webhook")
public class WebhookAdminController {

    private final SettingService settingService;
    private final WebhookDispatcherService dispatcher;
    private final AuditService auditService;

    public WebhookAdminController(SettingService settingService, WebhookDispatcherService dispatcher,
                                  AuditService auditService) {
        this.settingService = settingService;
        this.dispatcher = dispatcher;
        this.auditService = auditService;
    }

    @GetMapping("/config")
    public ResponseEntity<Object> getConfig(HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> cfg = settingService.getMap(WebhookDispatcherService.KEY_WEBHOOK_CONFIG);
        return ResponseEntity.ok(ApiResponse.success(null, cfg));
    }

    @PutMapping("/config")
    public ResponseEntity<Object> saveConfig(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", Boolean.TRUE.equals(body.get("enabled")));
        cfg.put("urls", body.get("urls") instanceof java.util.List<?> l ? l : java.util.List.of());
        cfg.put("events", body.get("events") instanceof java.util.List<?> l ? l : java.util.List.of());
        settingService.set(WebhookDispatcherService.KEY_WEBHOOK_CONFIG, cfg);
        auditService.log("settings_webhook", me, null, "Webhook 配置已保存");
        return ResponseEntity.ok(ApiResponse.success("Webhook 配置已保存"));
    }

    @PostMapping("/test")
    public ResponseEntity<Object> test(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> cfg = settingService.getMap(WebhookDispatcherService.KEY_WEBHOOK_CONFIG);
        if (!(cfg.getOrDefault("enabled", Boolean.FALSE) instanceof Boolean b) || !b) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("请先启用 Webhook"));
        }
        // 同步投递并把每个目标 的真实响应带回给管理端(飞书 9499/19021 等错误可见)
        List<Map<String, Object>> results = new ArrayList<>();
        for (var r : dispatcher.dispatchSync("webhook.test", Map.of(
                "operator", me == null ? "" : me,
                "message", "DreamPort Webhook 测试事件"))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("url", r.url());
            item.put("ok", r.ok());
            item.put("status", r.status());
            String resp = r.response() == null ? "" : r.response();
            item.put("response", resp.length() > 300 ? resp.substring(0, 300) : resp);
            results.add(item);
        }
        auditService.log("webhook_test", me, null, null);
        boolean allOk = results.stream().allMatch(x -> Boolean.TRUE.equals(x.get("ok")));
        return ResponseEntity.ok(ApiResponse.success(allOk ? "全部投递成功" : "部分目标投递失败(见详情)",
                Map.of("results", results)));
    }

    private static boolean admin(HttpServletRequest request) {
        return AuthUtil.currentUser(request) != null && AuthUtil.isAdmin(request);
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
    }
}
