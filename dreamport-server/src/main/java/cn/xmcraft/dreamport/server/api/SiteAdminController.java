package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditRepository;
import cn.xmcraft.dreamport.server.docs.DocsService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点管理端点（契约对齐旧版）：portal/background/server-config/system-config/upload/export/docs。
 * 站点内容写 dp_setting（修复旧版写 config.yml 重启易丢，Rules.md §9-6）。
 */
@RestController
@RequestMapping("/api")
public class SiteAdminController {

    private final SettingService settingService;
    private final UserRepository userRepository;
    private final AuditRepository auditRepository;
    private final DocsService docsService;

    public SiteAdminController(SettingService settingService, UserRepository userRepository,
                               AuditRepository auditRepository, DocsService docsService) {
        this.settingService = settingService;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.docsService = docsService;
    }

    // ---------- 门户 / 背景 / 公告 ----------

    @GetMapping("/admin/portal")
    public ResponseEntity<Object> getPortal(HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> portal = settingService.getMap(SettingService.KEY_PORTAL);
        if (portal.isEmpty()) {
            portal.put("server_name", "夏日小镇");
            portal.put("subtitle", "Minecraft 服务器");
            portal.put("description", "一个有趣、友好的 Minecraft 生存服务器，欢迎每一位玩家加入！");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", Map.of("portal", portal));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/admin/portal")
    public ResponseEntity<Object> setPortal(@RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        // 合并保存：保留未提交的键（team/carousel/features/timeline 等子端点分别写入）
        Map<String, Object> merged = settingService.getMap(SettingService.KEY_PORTAL);
        merged.putAll(body);
        settingService.set(SettingService.KEY_PORTAL, merged);
        return ResponseEntity.ok(ApiResponse.success("门户配置已保存"));
    }

    /** 门户子项保存（team/carousel/features/timeline，契约对齐旧版四个子端点） */
    @PostMapping({"/admin/portal/team", "/admin/portal/carousel", "/admin/portal/features", "/admin/portal/timeline"})
    public ResponseEntity<Object> setPortalSection(@RequestBody List<Map<String, Object>> items,
                                                   HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        String uri = request.getRequestURI();
        String key = uri.endsWith("/team") ? "team"
                : uri.endsWith("/carousel") ? "carousel"
                : uri.endsWith("/features") ? "features" : "timeline";
        Map<String, Object> portal = settingService.getMap(SettingService.KEY_PORTAL);
        portal.put(key, items);
        settingService.set(SettingService.KEY_PORTAL, portal);
        return ResponseEntity.ok(ApiResponse.success("已保存"));
    }

    @GetMapping("/admin/background")
    public ResponseEntity<Object> getBackground(HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> background = settingService.getMap(SettingService.KEY_BACKGROUND);
        background.putIfAbsent("image", "/bg.png");
        background.putIfAbsent("opacity", 0.55);
        background.putIfAbsent("blur", 20);
        Object announcement = settingService.get(SettingService.KEY_ANNOUNCEMENT, String.class);
        background.put("announcement", announcement == null ? "欢迎来到夏日小镇！" : announcement);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", background);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/admin/background")
    public ResponseEntity<Object> setBackground(@RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> background = settingService.getMap(SettingService.KEY_BACKGROUND);
        if (body.containsKey("image")) {
            background.put("image", body.get("image"));
        }
        if (body.containsKey("opacity")) {
            background.put("opacity", body.get("opacity"));
        }
        if (body.containsKey("blur")) {
            background.put("blur", body.get("blur"));
        }
        settingService.set(SettingService.KEY_BACKGROUND, background);
        if (body.containsKey("announcement")) {
            settingService.set(SettingService.KEY_ANNOUNCEMENT, body.get("announcement"));
        }
        return ResponseEntity.ok(ApiResponse.success("背景与公告已保存"));
    }

    // ---------- 服务器配置 / 系统配置 ----------

    @GetMapping("/admin/server-config")
    public ResponseEntity<Object> serverConfig(HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", Map.of("officialUuid",
                settingService.getMap("verify.config").getOrDefault("officialUuid", false)));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/admin/server-config")
    public ResponseEntity<Object> setServerConfig(@RequestBody Map<String, Object> body,
                                                  HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        settingService.set("verify.config", body);
        return ResponseEntity.ok(ApiResponse.success("已保存"));
    }

    @GetMapping("/admin/system-config")
    public ResponseEntity<Object> systemConfig(HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("admins", settingService.get(SettingService.KEY_ADMINS, List.class));
        config.put("adminNotifyEmail", settingService.get(SettingService.KEY_ADMIN_NOTIFY_EMAIL, String.class));
        config.put("portal", settingService.getMap(SettingService.KEY_PORTAL));
        config.put("astrbotApiToken", settingService.get("astrbot.api_token", String.class));
        return ResponseEntity.ok(config);
    }

    @PostMapping("/admin/system-config")
    public ResponseEntity<Object> setSystemConfig(@RequestBody Map<String, Object> body,
                                                  HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        if (body.containsKey("admins")) {
            settingService.set(SettingService.KEY_ADMINS, body.get("admins"));
        }
        if (body.containsKey("adminNotifyEmail")) {
            settingService.set(SettingService.KEY_ADMIN_NOTIFY_EMAIL, body.get("adminNotifyEmail"));
        }
        if (body.containsKey("astrbotApiToken")) {
            settingService.set("astrbot.api_token", body.get("astrbotApiToken"));
        }
        return ResponseEntity.ok(ApiResponse.success("系统配置已保存"));
    }

    // ---------- 上传 ----------

    @PostMapping("/admin/upload")
    public ResponseEntity<Object> upload(@RequestParam("file") MultipartFile file,
                                         HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("文件为空"));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("文件不能超过 5MB"));
        }
        String ext = detectExt(file.getOriginalFilename());
        if (ext == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("不支持的文件类型"));
        }
        String filename = java.util.UUID.randomUUID() + ext;
        try {
            Path dir = Path.of("static", "uploads");
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toAbsolutePath());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.failure("上传失败"));
        }
        return ResponseEntity.ok(ApiResponse.success("上传成功",
                Map.of("url", "/uploads/" + filename, "filename", filename)));
    }

    private String detectExt(String filename) {
        if (filename == null) {
            return null;
        }
        String lower = filename.toLowerCase();
        for (String ext : List.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg")) {
            if (lower.endsWith(ext)) {
                return ext;
            }
        }
        return null;
    }

    // ---------- 导出（CSV 带 UTF-8 BOM / JSON） ----------

    @GetMapping("/admin/export/users")
    public ResponseEntity<Object> exportUsers(@RequestParam(defaultValue = "csv") String format,
                                              HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=users.json")
                    .body(userRepository.findAll());
        }
        StringBuilder csv = new StringBuilder("\uFEFFUsername,Email,Status,RegTime,MinecraftName,BedrockName\n");
        userRepository.findAll().forEach(u -> csv.append(csvRow(List.of(
                orEmpty(u.username()), orEmpty(u.email()), orEmpty(u.status()), String.valueOf(u.regTime()),
                orEmpty(u.minecraftName()), orEmpty(u.bedrockName())))));
        return csvFile(csv, "users.csv");
    }

    @GetMapping("/admin/export/audits")
    public ResponseEntity<Object> exportAudits(@RequestParam(defaultValue = "csv") String format,
                                               HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=audits.json")
                    .body(auditRepository.findAll());
        }
        StringBuilder csv = new StringBuilder("\uFEFFID,Action,Operator,Target,Detail,Time\n");
        auditRepository.findAll().forEach(a -> csv.append(csvRow(List.of(
                String.valueOf(a.id()), orEmpty(a.action()), orEmpty(a.operator()),
                orEmpty(a.target()), orEmpty(a.detail()), String.valueOf(a.createdAt())))));
        return csvFile(csv, "audits.csv");
    }

    private ResponseEntity<Object> csvFile(StringBuilder csv, String filename) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + filename)
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.toString());
    }

    private String csvRow(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i);
            boolean needQuote = cell.contains(",") || cell.contains("\"") || cell.contains("\n");
            if (needQuote) {
                sb.append('"').append(cell.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(cell);
            }
            if (i < cells.size() - 1) {
                sb.append(',');
            }
        }
        return sb.append('\n').toString();
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }

    // ---------- 工具 ----------

    private boolean admin(HttpServletRequest request) {
        return AuthUtil.currentUser(request) != null && AuthUtil.isAdmin(request);
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
    }
}
