package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.infra.SensitiveWordFilter;
import cn.xmcraft.dreamport.server.infra.SimpleRateLimiter;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.websocket.ReviewPushService;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 反馈工单(多轮对话式):玩家提交建议/问题 → 管理员回复(铃铛+邮件) → 玩家可追问 → 关闭。
 * 表:dp_feedback(会话) + dp_feedback_message(消息)。审计 + WS 管理端推送全挂。
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private static final int DAILY_LIMIT = 3;

    private final JdbcTemplate jdbc;
    private final SettingService settingService;
    private final UserRepository userRepository;
    private final cn.xmcraft.dreamport.server.infra.MailService mailService;
    private final AuditService auditService;
    private final ReviewPushService pushService;
    private final SimpleRateLimiter rateLimiter;
    private final SensitiveWordFilter sensitiveWordFilter;

    public FeedbackController(JdbcTemplate jdbc, SettingService settingService, UserRepository userRepository,
                              cn.xmcraft.dreamport.server.infra.MailService mailService,
                              AuditService auditService, ReviewPushService pushService,
                              SimpleRateLimiter rateLimiter, SensitiveWordFilter sensitiveWordFilter) {
        this.jdbc = jdbc;
        this.settingService = settingService;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.auditService = auditService;
        this.pushService = pushService;
        this.rateLimiter = rateLimiter;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    public record CreateBody(String category, String title, String content) {
    }

    public record ReplyBody(String content) {
    }

    /** 提交新工单(每天最多 3 条) */
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody CreateBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        if (body.title() == null || body.title().isBlank() || body.content() == null || body.content().isBlank()) {
            return badRequest("标题与内容必填");
        }
        if (body.title().length() > 128) {
            return badRequest("标题过长(≤128 字)");
        }
        String category = switch (body.category() == null ? "" : body.category()) {
            case "suggestion", "bug", "other" -> body.category();
            default -> "other";
        };
        if (!rateLimiter.allowDaily("feedback:" + me.toLowerCase(), DAILY_LIMIT)) {
            return badRequest("今天提交已达上限(" + DAILY_LIMIT + " 条),请明天再来");
        }
        long now = System.currentTimeMillis();
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO dp_feedback (username, category, title, status, created_at, updated_at) VALUES (?, ?, ?, 'open', ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, me);
            ps.setString(2, category);
            ps.setString(3, sensitiveWordFilter.filter(body.title().trim()));
            ps.setLong(4, now);
            ps.setLong(5, now);
            return ps;
        }, keyHolder);
        Long id = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        if (id == null) {
            return ResponseEntity.internalServerError().body(ApiResponse.failure("工单创建失败"));
        }
        jdbc.update("INSERT INTO dp_feedback_message (feedback_id, sender_role, sender, content, created_at) "
                        + "VALUES (?, 'player', ?, ?, ?)",
                id, me, sensitiveWordFilter.filter(body.content().trim()), now);
        auditService.log("feedback_create", me, "#" + id, category + ": " + body.title());
        pushService.pushEvent("feedback_new", Map.of("id", String.valueOf(id), "username", me));
        return ResponseEntity.ok(ApiResponse.success("已提交,管理员会尽快回复", Map.of("id", id)));
    }

    /** 我的工单列表 */
    @GetMapping("/mine")
    public ResponseEntity<Object> mine(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT id, category, title, status, created_at, updated_at FROM dp_feedback "
                        + "WHERE username = ? ORDER BY updated_at DESC, id DESC", me);
        return ResponseEntity.ok(Map.of("success", true, "data", list));
    }

    /** 会话详情(玩家本人或管理员) */
    @GetMapping("/{id}")
    public ResponseEntity<Object> detail(@PathVariable long id, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        Map<String, Object> fb = findOne(id);
        if (fb == null) {
            return badRequest("工单不存在");
        }
        boolean owner = me.equalsIgnoreCase(String.valueOf(fb.get("username")));
        if (!owner && !isAdminOfSite(request)) {
            return forbidden();
        }
        Map<String, Object> data = new LinkedHashMap<>(fb);
        data.put("messages", jdbc.queryForList(
                "SELECT id, sender_role, sender, content, created_at FROM dp_feedback_message "
                        + "WHERE feedback_id = ? ORDER BY created_at, id", id));
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    /** 追问(玩家,关闭后不可再回) */
    @PostMapping("/{id}/reply")
    public ResponseEntity<Object> reply(@PathVariable long id, @RequestBody ReplyBody body,
                                        HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        Map<String, Object> fb = findOne(id);
        if (fb == null || !me.equalsIgnoreCase(String.valueOf(fb.get("username")))) {
            return forbidden();
        }
        if ("closed".equals(String.valueOf(fb.get("status")))) {
            return badRequest("工单已关闭,如仍有问题请新建工单");
        }
        if (body.content() == null || body.content().isBlank()) {
            return badRequest("内容必填");
        }
        if (!rateLimiter.allowWindow("feedback-reply:" + me.toLowerCase(), 5, 60_000)) {
            return badRequest("发送太频繁,请稍后再试");
        }
        jdbc.update("INSERT INTO dp_feedback_message (feedback_id, sender_role, sender, content, created_at) "
                        + "VALUES (?, 'player', ?, ?, ?)",
                id, me, sensitiveWordFilter.filter(body.content().trim()), System.currentTimeMillis());
        jdbc.update("UPDATE dp_feedback SET status = 'open', updated_at = ? WHERE id = ?",
                System.currentTimeMillis(), id);
        pushService.pushEvent("feedback_new", Map.of("id", String.valueOf(id), "username", me));
        return ResponseEntity.ok(ApiResponse.success("已发送"));
    }

    /** 管理员回复 → 玩家铃铛 + 邮件 */
    @PostMapping("/{id}/admin/reply")
    public ResponseEntity<Object> adminReply(@PathVariable long id, @RequestBody ReplyBody body,
                                             HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        Map<String, Object> fb = findOne(id);
        if (fb == null) {
            return badRequest("工单不存在");
        }
        if (body.content() == null || body.content().isBlank()) {
            return badRequest("内容必填");
        }
        String owner = String.valueOf(fb.get("username"));
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO dp_feedback_message (feedback_id, sender_role, sender, content, created_at) "
                        + "VALUES (?, 'admin', ?, ?, ?)", id, me,
                sensitiveWordFilter.filter(body.content().trim()), now);
        jdbc.update("UPDATE dp_feedback SET status = 'answered', updated_at = ? WHERE id = ?", now, id);
        userRepository.findByUsernameIgnoreCase(owner).ifPresent(u -> {
            String preview = body.content().length() > 200 ? body.content().substring(0, 200) + "…" : body.content();
            mailService.sendFeedbackReply(u.username(), u.email(), String.valueOf(fb.get("title")), preview, "zh");
        });
        auditService.log("feedback_reply", me, "#" + id, "回复工单: " + fb.get("title"));
        return ResponseEntity.ok(ApiResponse.success("已回复"));
    }

    /** 关闭(玩家确认解决或管理员关闭) */
    @PostMapping("/{id}/close")
    public ResponseEntity<Object> close(@PathVariable long id, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        Map<String, Object> fb = findOne(id);
        if (fb == null) {
            return badRequest("工单不存在");
        }
        boolean owner = me.equalsIgnoreCase(String.valueOf(fb.get("username")));
        if (!owner && !isAdminOfSite(request)) {
            return forbidden();
        }
        jdbc.update("UPDATE dp_feedback SET status = 'closed', updated_at = ? WHERE id = ?",
                System.currentTimeMillis(), id);
        auditService.log("feedback_close", me, "#" + id, owner ? "玩家确认解决" : "管理员关闭");
        return ResponseEntity.ok(ApiResponse.success("已关闭"));
    }

    /** 管理员:按状态列工单 */
    @GetMapping("/admin/list")
    public ResponseEntity<Object> adminList(@RequestParam(required = false) String status,
                                            HttpServletRequest request) {
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        List<Map<String, Object>> list = (status == null || status.isBlank())
                ? jdbc.queryForList("SELECT id, username, category, title, status, created_at, updated_at "
                        + "FROM dp_feedback ORDER BY updated_at DESC, id DESC LIMIT 200")
                : jdbc.queryForList("SELECT id, username, category, title, status, created_at, updated_at "
                        + "FROM dp_feedback WHERE status = ? ORDER BY updated_at DESC, id DESC LIMIT 200", status);
        return ResponseEntity.ok(Map.of("success", true, "data", list));
    }

    private Map<String, Object> findOne(long id) {
        var rows = jdbc.queryForList("SELECT id, username, category, title, status, created_at, updated_at "
                + "FROM dp_feedback WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean isAdminOfSite(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return false;
        }
        if (AuthUtil.isAdmin(request)) {
            return true;
        }
        List<String> admins = settingService.get(SettingService.KEY_ADMINS, List.class);
        return admins != null && admins.stream().anyMatch(a -> String.valueOf(a).equalsIgnoreCase(me));
    }

    private ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
    }

    private ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(message));
    }
}
