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
 * 站点 SEO 配置(后台「门户管理 → SEO 搜索优化」):
 * {titleTemplate, description, keywords, ogImage, robots: index|noindex, extraHead}。
 * extraHead 为管理员自定义 head 注入(站点验证码/统计脚本等,原样输出,仅管理员可写)。
 */
@RestController
@RequestMapping("/api/admin/seo")
public class SeoAdminController {

    private final SettingService settingService;
    private final AuditService auditService;

    public SeoAdminController(SettingService settingService, AuditService auditService) {
        this.settingService = settingService;
        this.auditService = auditService;
    }

    @GetMapping("/config")
    public ResponseEntity<Object> getConfig(HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        return ResponseEntity.ok(ApiResponse.success(null, settingService.getMap(SettingService.KEY_SEO_CONFIG)));
    }

    @PutMapping("/config")
    public ResponseEntity<Object> saveConfig(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("titleTemplate", clipped(body.get("titleTemplate"), 120));
        cfg.put("description", clipped(body.get("description"), 500));
        cfg.put("keywords", clipped(body.get("keywords"), 500));
        cfg.put("ogImage", clipped(body.get("ogImage"), 500));
        String robots = String.valueOf(body.getOrDefault("robots", "index"));
        cfg.put("robots", "noindex".equals(robots) ? "noindex" : "index");
        cfg.put("extraHead", clipped(body.get("extraHead"), 8000));
        settingService.set(SettingService.KEY_SEO_CONFIG, cfg);
        auditService.log("settings_seo", me, null, "SEO 设置已保存");
        return ResponseEntity.ok(ApiResponse.success("SEO 设置已保存"));
    }

    private static String clipped(Object o, int max) {
        String s = o == null ? "" : String.valueOf(o).trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static boolean admin(HttpServletRequest request) {
        return AuthUtil.currentUser(request) != null && AuthUtil.isAdmin(request);
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
    }
}
