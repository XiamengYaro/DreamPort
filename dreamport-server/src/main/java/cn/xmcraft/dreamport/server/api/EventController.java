package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动日历:管理员创建/管理活动,玩家在社区页日历查看并报名。
 * 报名人数公开可见;报名名单仅管理端可见。
 */
@RestController
@RequestMapping("/api")
public class EventController {

    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final SettingService settingService;

    public EventController(JdbcTemplate jdbc, AuditService auditService, SettingService settingService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.settingService = settingService;
    }

    public record EventBody(String title, String description, Long eventAt) {
    }

    /** 公开:活动列表(含报名人数;登录者附带自己是否已报) */
    @GetMapping("/events")
    public ResponseEntity<Object> list(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT id, title, description, event_at, created_by, created_at FROM dp_event ORDER BY event_at ASC");
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (var e : events) {
            long id = ((Number) e.get("id")).longValue();
            Map<String, Object> m = new LinkedHashMap<>(e);
            m.put("signups", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dp_event_signup WHERE event_id = ?", Integer.class, id));
            if (me != null) {
                m.put("signedUp", jdbc.queryForObject(
                        "SELECT COUNT(*) FROM dp_event_signup WHERE event_id = ? AND username = ?",
                        Integer.class, id, me) > 0);
            }
            result.add(m);
        }
        return ResponseEntity.ok(ApiResponse.success(null, Map.of("events", result)));
    }

    /** 报名/取消报名(JWT) */
    @PostMapping("/events/{id}/signup")
    public ResponseEntity<Object> signup(@PathVariable long id, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        var ev = jdbc.queryForList("SELECT id FROM dp_event WHERE id = ?", id);
        if (ev.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("活动不存在"));
        }
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dp_event_signup WHERE event_id = ? AND username = ?",
                Integer.class, id, me);
        if (exists != null && exists > 0) {
            jdbc.update("DELETE FROM dp_event_signup WHERE event_id = ? AND username = ?", id, me);
            return ResponseEntity.ok(ApiResponse.success("已取消报名"));
        }
        jdbc.update("INSERT INTO dp_event_signup (event_id, username, created_at) VALUES (?, ?, ?)",
                id, me, System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.success("报名成功"));
    }

    // ---------- 管理端 ----------

    private boolean admin(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) return false;
        if (AuthUtil.isAdmin(request)) return true;
        List<String> admins = settingService.get(SettingService.KEY_ADMINS, List.class);
        return admins != null && admins.stream().anyMatch(a -> String.valueOf(a).equalsIgnoreCase(me));
    }

    @PostMapping("/admin/events")
    public ResponseEntity<Object> create(@RequestBody EventBody body, HttpServletRequest request) {
        if (!admin(request)) return forbidden();
        if (body.title() == null || body.title().isBlank() || body.eventAt() == null) {
            return badRequest("标题与活动时间必填");
        }
        jdbc.update("INSERT INTO dp_event (title, description, event_at, created_by, created_at) VALUES (?, ?, ?, ?, ?)",
                body.title().trim(), body.description() == null ? "" : body.description().trim(),
                body.eventAt(), AuthUtil.currentUser(request), System.currentTimeMillis());
        auditService.log("event_create", AuthUtil.currentUser(request), body.title().trim(), null);
        return ResponseEntity.ok(ApiResponse.success("活动已创建"));
    }

    @PutMapping("/admin/events/{id}")
    public ResponseEntity<Object> update(@PathVariable long id, @RequestBody EventBody body,
                                         HttpServletRequest request) {
        if (!admin(request)) return forbidden();
        jdbc.update("UPDATE dp_event SET title = ?, description = ?, event_at = ? WHERE id = ?",
                body.title().trim(), body.description() == null ? "" : body.description().trim(),
                body.eventAt(), id);
        auditService.log("event_update", AuthUtil.currentUser(request), "#" + id, body.title());
        return ResponseEntity.ok(ApiResponse.success("活动已更新"));
    }

    @DeleteMapping("/admin/events/{id}")
    public ResponseEntity<Object> delete(@PathVariable long id, HttpServletRequest request) {
        if (!admin(request)) return forbidden();
        jdbc.update("DELETE FROM dp_event_signup WHERE event_id = ?", id);
        jdbc.update("DELETE FROM dp_event WHERE id = ?", id);
        auditService.log("event_delete", AuthUtil.currentUser(request), "#" + id, null);
        return ResponseEntity.ok(ApiResponse.success("活动已删除"));
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
    }

    private ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(message));
    }

    /** 报名名单(管理端) */
    @GetMapping("/admin/events/{id}/signups")
    public ResponseEntity<Object> signups(@PathVariable long id, HttpServletRequest request) {
        if (!admin(request)) return forbidden();
        return ResponseEntity.ok(ApiResponse.success(null, Map.of("list",
                jdbc.queryForList("SELECT username, created_at FROM dp_event_signup WHERE event_id = ? ORDER BY created_at", id))));
    }
}
