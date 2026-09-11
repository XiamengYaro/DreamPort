package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 玩家个人主页自定义(简介/横幅主题色/社交链接):
 * GET/PUT /api/user/profile-custom(JWT 本人);公开资料页经 /api/players/profile 带出。
 * 安全:bio 纯文本渲染(前端 {{ }} 插值,非 v-html);social url 仅放行 http/https(防 javascript:)。
 */
@RestController
@RequestMapping("/api/user/profile-custom")
public class ProfileCustomController {

    private static final Pattern SAFE_URL = Pattern.compile("^https?://[\\w.-]+.*$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_LINKS = 5;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProfileCustomController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SocialLink(String label, String url) {
    }

    public record ProfileBody(String bio, String banner, List<SocialLink> socialLinks,
                              String bgImage, String bannerImage, String accent, String css) {
    }

    @GetMapping
    public ResponseEntity<Object> get(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        return ResponseEntity.ok(ApiResponse.success(null, readRow(jdbc, mapper, me)));
    }

    @PutMapping
    public ResponseEntity<Object> save(@RequestBody ProfileBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        String bio = body.bio() == null ? "" : body.bio().trim();
        if (bio.length() > 500) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("简介过长(≤500 字)"));
        }
        String banner = body.banner() == null || body.banner().isBlank()
                ? "amber" : body.banner().trim().toLowerCase();
        if (!banner.matches("[a-z]+")) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("横幅色键非法"));
        }
        List<SocialLink> links = new ArrayList<>();
        if (body.socialLinks() != null) {
            for (SocialLink l : body.socialLinks()) {
                if (links.size() >= MAX_LINKS) break;
                if (l == null || l.url() == null || l.url().isBlank()) continue;
                String url = l.url().trim();
                if (!SAFE_URL.matcher(url).matches()) {
                    return ResponseEntity.badRequest().body(ApiResponse.failure("链接仅支持 http/https"));
                }
                String label = l.label() == null || l.label().isBlank() ? "链接" : l.label().trim();
                links.add(new SocialLink(label.length() > 20 ? label.substring(0, 20) : label, url));
            }
        }
        String bgImage = cleanUrlField(body.bgImage());
        String bannerImage = cleanUrlField(body.bannerImage());
        String accent = body.accent() == null ? "" : body.accent().trim();
        if (!accent.isEmpty() && !accent.matches("#[0-9a-fA-F]{6}")) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("主题色格式非法,需 #rrggbb"));
        }
        String css = cn.xmcraft.dreamport.server.infra.CssSanitizer.sanitize(
                body.css() == null ? "" : body.css(), "#pc-root", 8000);
        String linksJson = writeJson(links);
        long now = System.currentTimeMillis();
        var exists = jdbc.queryForList("SELECT id FROM dp_user_profile WHERE username = ?", me);
        if (exists.isEmpty()) {
            jdbc.update("INSERT INTO dp_user_profile (username, bio, banner, social_links, bg_image, banner_image, accent, css, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    me, bio, banner, linksJson, bgImage, bannerImage, accent, css, now);
        } else {
            jdbc.update("UPDATE dp_user_profile SET bio = ?, banner = ?, social_links = ?, bg_image = ?, banner_image = ?, "
                            + "accent = ?, css = ?, updated_at = ? WHERE username = ?",
                    bio, banner, linksJson, bgImage, bannerImage, accent, css, now, me);
        }
        return ResponseEntity.ok(ApiResponse.success("主页自定义已保存"));
    }

    /** 公开资料接口取数用(包内可见):按 username 读取,异常返回空 */
    static Map<String, Object> readRow(JdbcTemplate jdbc, ObjectMapper mapper, String username) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bio", "");
        data.put("banner", "amber");
        data.put("socialLinks", new ArrayList<>());
        try {
            var rows = jdbc.queryForList(
                    "SELECT bio, banner, social_links, bg_image, banner_image, accent, css, updated_at "
                            + "FROM dp_user_profile WHERE username = ?", username);
            if (rows.isEmpty()) {
                return data;
            }
            var r = rows.get(0);
            data.put("bio", r.get("bio") == null ? "" : String.valueOf(r.get("bio")));
            data.put("banner", r.get("banner") == null ? "amber" : String.valueOf(r.get("banner")));
            data.put("bgImage", r.get("bg_image") == null ? "" : String.valueOf(r.get("bg_image")));
            data.put("bannerImage", r.get("banner_image") == null ? "" : String.valueOf(r.get("banner_image")));
            data.put("accent", r.get("accent") == null ? "" : String.valueOf(r.get("accent")));
            data.put("css", r.get("css") == null ? "" : String.valueOf(r.get("css")));
            data.put("bgImage", r.get("bg_image") == null ? "" : String.valueOf(r.get("bg_image")));
            data.put("bannerImage", r.get("banner_image") == null ? "" : String.valueOf(r.get("banner_image")));
            data.put("accent", r.get("accent") == null ? "" : String.valueOf(r.get("accent")));
            data.put("css", r.get("css") == null ? "" : String.valueOf(r.get("css")));
            String linksJson = r.get("social_links") == null ? "[]" : String.valueOf(r.get("social_links"));
            ObjectMapper m = new ObjectMapper();
            List<Map<String, Object>> links = new ArrayList<>();
            for (var node : m.readTree(linksJson)) {
                if (links.size() >= MAX_LINKS) break;
                String url = node.path("url").asText("");
                if (!SAFE_URL.matcher(url).matches()) continue;
                links.add(Map.of("label", node.path("label").asText("链接"), "url", url));
            }
            data.put("socialLinks", links);
        } catch (Exception ignored) {
        }
        return data;
    }

    /** 上传通道返回的 /uploads/ 路径或空 */
    private String cleanUrlField(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim();
        return u.startsWith("/uploads/") && !u.contains("..") ? u : null;
    }

    private String writeJson(List<SocialLink> links) {
        try {
            return mapper.writeValueAsString(links);
        } catch (Exception e) {
            return "[]";
        }
    }
}
