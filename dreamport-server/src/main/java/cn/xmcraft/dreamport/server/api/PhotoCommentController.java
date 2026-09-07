package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import cn.xmcraft.dreamport.server.web.RateLimiter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 照片墙留言（首页时光照片墙）：公开读、登录写、管理员删。
 * 按 photoKey（时间线条目稳定键）存取，存 dp_photo_comment。
 */
@RestController
@RequestMapping("/api")
public class PhotoCommentController {

    private final JdbcTemplate jdbc;
    private final cn.xmcraft.dreamport.server.web.RateLimiter rateLimiter;
    private final cn.xmcraft.dreamport.server.settings.SettingService settingService;

    public PhotoCommentController(JdbcTemplate jdbc, cn.xmcraft.dreamport.server.web.RateLimiter rateLimiter,
                                  cn.xmcraft.dreamport.server.settings.SettingService settingService) {
        this.jdbc = jdbc;
        this.rateLimiter = rateLimiter;
        this.settingService = settingService;
    }

    /** 先审后发开关(dp_setting photo.comment.moderation,默认关=先发后显) */
    private boolean moderationOn() {
        return settingService.getBool("photo.comment.moderation", false);
    }

    /** 敏感词过滤(与聊天同词库,命中替换 ***) */
    private String filterSensitive(String text) {
        String words = settingService.getRaw("sensitive.words");
        if (words == null || words.isBlank()) return text;
        for (String w : words.split("[,，]")) {
            String word = w.trim();
            if (word.length() >= 2 && text.contains(word)) {
                text = text.replace(word, "*".repeat(word.length()));
            }
        }
        return text;
    }

    /** 留言列表（公开,倒序,最多 200 条） */
    @GetMapping("/portal/comments/{photoKey}")
    public Map<String, Object> list(@PathVariable String photoKey) {
        List<Map<String, Object>> comments = jdbc.queryForList(
                "SELECT id, username, content, created_at FROM dp_photo_comment "
                        + "WHERE photo_key = ? AND approved = TRUE ORDER BY created_at DESC, id DESC LIMIT 200",
                photoKey);
        return Map.of("success", true, "data", Map.of("comments", comments));
    }

    /** 待审评论(管理员) */
    @GetMapping("/admin/portal/comments/pending")
    public Map<String, Object> pending(jakarta.servlet.http.HttpServletRequest request) {
        if (!(AuthUtil.currentUser(request) != null && AuthUtil.isAdmin(request))) {
            return ApiResponse.failure("需要管理员权限");
        }
        List<Map<String, Object>> comments = jdbc.queryForList(
                "SELECT id, photo_key, username, content, created_at FROM dp_photo_comment "
                        + "WHERE approved = FALSE ORDER BY created_at DESC LIMIT 200");
        return Map.of("success", true, "data", Map.of("comments", comments));
    }

    /** 通过待审评论(管理员) */
    @PostMapping("/admin/portal/comments/{id}/approve")
    public Map<String, Object> approveComment(@PathVariable long id, jakarta.servlet.http.HttpServletRequest request) {
        if (!(AuthUtil.currentUser(request) != null && AuthUtil.isAdmin(request))) {
            return ApiResponse.failure("需要管理员权限");
        }
        jdbc.update("UPDATE dp_photo_comment SET approved = TRUE WHERE id = ?", id);
        return ApiResponse.success("已通过");
    }

    /** 各照片留言数(照片墙网格角标用) */
    @GetMapping("/portal/comments/counts")
    public Map<String, Object> counts() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT photo_key, COUNT(*) AS cnt FROM dp_photo_comment GROUP BY photo_key");
        Map<String, Object> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put(String.valueOf(row.get("photo_key")), ((Number) row.get("cnt")).intValue());
        }
        return Map.of("success", true, "data", Map.of("counts", counts));
    }

    /** 发表留言(登录用户) */
    @PostMapping("/portal/comments/{photoKey}")
    public Map<String, Object> post(@PathVariable String photoKey,
                                    @RequestBody Map<String, String> body,
                                    jakarta.servlet.http.HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ApiResponse.failure("登录后才能留言");
        }
        String content = body == null || body.get("content") == null ? "" : body.get("content").trim();
        if (content.isEmpty()) {
            return ApiResponse.failure("留言内容不能为空");
        }
        if (content.length() > 500) {
            return ApiResponse.failure("留言过长(上限 500 字)");
        }
        if (!rateLimiter.allow("photo-comment:" + me + ":" + request.getRemoteAddr(), 5, 60_000)) {
            return ApiResponse.failure("留言太快,稍后再试");
        }
        content = filterSensitive(content);
        boolean approved = !moderationOn();
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO dp_photo_comment (photo_key, username, content, created_at, approved) VALUES (?, ?, ?, ?, ?)",
                photoKey, me, content, now, approved);
        if (!approved) {
            return ApiResponse.success("留言已提交,审核通过后展示", Map.of("pendingReview", true));
        }
        Map<String, Object> comment = new LinkedHashMap<>();
        comment.put("username", me);
        comment.put("content", content);
        comment.put("created_at", now);
        return ApiResponse.success("留言已发布", comment);
    }

    /** 删除留言(管理员) */
    @DeleteMapping("/admin/portal/comments/{id}")
    public Map<String, Object> delete(@PathVariable long id, jakarta.servlet.http.HttpServletRequest request) {
        if (!AuthUtil.isAdmin(request)) {
            return ApiResponse.failure("需要管理员权限");
        }
        jdbc.update("DELETE FROM dp_photo_comment WHERE id = ?", id);
        return ApiResponse.success("已删除");
    }
}
