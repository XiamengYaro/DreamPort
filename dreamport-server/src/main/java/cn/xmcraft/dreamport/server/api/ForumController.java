package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.infra.SensitiveWordFilter;
import cn.xmcraft.dreamport.server.infra.SimpleRateLimiter;
import cn.xmcraft.dreamport.server.notification.NotificationRecord;
import cn.xmcraft.dreamport.server.notification.NotificationRepository;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.websocket.ReviewPushService;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 论坛(进阶版):板块/帖子/回复/点赞/@提及/编辑留痕 + 先发后审(默认)/先审后发可切换。
 * 游客可读;发帖 3 条/天、回帖 10 条/天、30s 限频;审核动作统一挂审计+通知+WS 推送。
 */
@RestController
@RequestMapping("/api/forum")
public class ForumController {

    private static final int PAGE_SIZE = 20;
    private static final long EDIT_WINDOW_MS = 10 * 60_000L;
    private static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9_\\-\\u4e00-\\u9fa5]{2,32})");

    private final JdbcTemplate jdbc;
    private final SettingService settingService;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ReviewPushService pushService;
    private final SimpleRateLimiter rateLimiter;
    private final SensitiveWordFilter sensitiveWordFilter;

    public ForumController(JdbcTemplate jdbc, SettingService settingService,
                           NotificationRepository notificationRepository, UserRepository userRepository,
                           AuditService auditService, ReviewPushService pushService,
                           SimpleRateLimiter rateLimiter, SensitiveWordFilter sensitiveWordFilter) {
        this.jdbc = jdbc;
        this.settingService = settingService;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.pushService = pushService;
        this.rateLimiter = rateLimiter;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    // ---------- 公开读(游客可读) ----------

    @GetMapping("/sections")
    public ResponseEntity<Object> sections() {
        return ResponseEntity.ok(Map.of("success", true, "data",
                jdbc.queryForList("SELECT id, name, description, sort, locked FROM dp_forum_section ORDER BY sort, id")));
    }

    /** 帖子列表:置顶优先,游客只看 published,作者可附带看到自己 pending */
    @GetMapping("/threads")
    public ResponseEntity<Object> threads(@RequestParam(required = false) Long sectionId,
                                          @RequestParam(defaultValue = "1") int page,
                                          HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        boolean admin = me != null && isAdminOfSite(request);
        int p = Math.max(1, page);
        StringBuilder where = new StringBuilder();
        List<Object> args = new java.util.ArrayList<>();
        if (admin) {
            // 管理员全量(审核需要)
        } else if (me != null) {
            where.append("(t.status = 'published' OR (t.status = 'pending' AND t.username = ?))");
            args.add(me);
        } else {
            where.append("t.status = 'published'");
        }
        if (sectionId != null) {
            where.append(where.isEmpty() ? "" : " AND ").append("t.section_id = ?");
            args.add(sectionId);
        }
        String whereSql = where.isEmpty() ? "" : "WHERE " + where;
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dp_forum_thread t " + whereSql.replace("t.", ""), Integer.class,
                args.toArray());
        List<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(PAGE_SIZE);
        pageArgs.add((p - 1) * PAGE_SIZE);
        List<Map<String, Object>> threads = jdbc.queryForList(
                "SELECT t.id, t.section_id, t.username, t.title, t.status, t.pinned, t.essence, t.locked, "
                        + "t.reply_count, t.like_count, t.last_reply_at, t.edited, t.created_at, "
                        + "(SELECT COUNT(*) FROM dp_forum_like l WHERE l.target_type = 'thread' AND l.target_id = t.id) AS likes "
                        + "FROM dp_forum_thread t " + whereSql + " "
                        + "ORDER BY t.pinned DESC, t.last_reply_at DESC, t.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "threads", threads, "total", total == null ? 0 : total, "page", p, "pageSize", PAGE_SIZE)));
    }

    /** 帖子详情 + 回复分页;游客可读 published */
    @GetMapping("/thread/{id}")
    public ResponseEntity<Object> thread(@PathVariable long id,
                                         @RequestParam(defaultValue = "1") int page,
                                         HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        boolean admin = me != null && isAdminOfSite(request);
        var rows = jdbc.queryForList("SELECT id, section_id, username, title, content, status, pinned, essence, "
                + "locked, edited, like_count, created_at, updated_at, reviewed_by FROM dp_forum_thread WHERE id = ?", id);
        if (rows.isEmpty()) {
            return badRequest("帖子不存在");
        }
        Map<String, Object> t = rows.get(0);
        String author = String.valueOf(t.get("username"));
        boolean statusOk = "published".equals(String.valueOf(t.get("status")));
        if (!statusOk && !admin && (me == null || !me.equalsIgnoreCase(author))) {
            return badRequest("帖子不存在或未通过审核");
        }
        int p = Math.max(1, page);
        String replyWhere = admin ? "thread_id = ? AND status IN ('published','pending')"
                : "thread_id = ? AND (status = 'published' OR (status = 'pending' AND username = ?))";
        Object[] replyArgs = admin ? new Object[]{id} : new Object[]{id, me == null ? "" : me};
        List<Map<String, Object>> replies = jdbc.queryForList(
                "SELECT id, username, content, status, created_at, "
                        + "(SELECT COUNT(*) FROM dp_forum_like l WHERE l.target_type = 'reply' AND l.target_id = dp_forum_reply.id) AS likes "
                        + "FROM dp_forum_reply WHERE " + replyWhere + " ORDER BY created_at, id LIMIT ? OFFSET ?",
                appendArgs(replyArgs, PAGE_SIZE, (p - 1) * PAGE_SIZE));
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dp_forum_reply WHERE " + replyWhere.replace("thread_id", "thread_id"),
                Integer.class, replyArgs);
        boolean myLike = me != null && jdbc.queryForObject(
                "SELECT COUNT(*) FROM dp_forum_like WHERE target_type = 'thread' AND target_id = ? AND username = ?",
                Integer.class, id, me) > 0;
        Map<String, Object> data = new LinkedHashMap<>(t);
        data.put("replies", replies);
        data.put("replyTotal", total == null ? 0 : total);
        data.put("replyPage", p);
        data.put("replyPageSize", PAGE_SIZE);
        data.put("myLike", myLike);
        data.put("mine", me != null && me.equalsIgnoreCase(author));
        data.put("canModerate", admin);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    // ---------- 玩家写 ----------

    public record ThreadBody(Long sectionId, String title, String content) {
    }

    public record ReplyBody(String content) {
    }

    @PostMapping("/thread")
    public ResponseEntity<Object> createThread(@RequestBody ThreadBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        if (body.sectionId() == null || body.title() == null || body.title().isBlank()
                || body.content() == null || body.content().isBlank()) {
            return badRequest("板块/标题/内容必填");
        }
        if (body.title().length() > 128 || body.content().length() > 20_000) {
            return badRequest("标题过长或内容超限");
        }
        var section = jdbc.queryForList("SELECT id, locked FROM dp_forum_section WHERE id = ?", body.sectionId());
        if (section.isEmpty()) {
            return badRequest("板块不存在");
        }
        if (((Number) section.get(0).get("locked")).intValue() != 0 && !isAdminOfSite(request)) {
            return badRequest("该板块已锁定,仅管理员可发帖");
        }
        if (!rateLimiter.allowWindow("thread:" + me.toLowerCase(), 1, 30_000)) {
            return badRequest("发帖太频繁,请 30 秒后再试");
        }
        if (!rateLimiter.allowDaily("thread-daily:" + me.toLowerCase(), 3)) {
            return badRequest("今天发帖已达上限(3 条)");
        }
        boolean moderation = forumConfig().getOrDefault("moderation", Boolean.FALSE) instanceof Boolean b && b;
        long now = System.currentTimeMillis();
        String status = moderation ? "pending" : "published";
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO dp_forum_thread (section_id, username, title, content, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, body.sectionId());
            ps.setString(2, me);
            ps.setString(3, sensitiveWordFilter.filter(body.title().trim()));
            ps.setString(4, sensitiveWordFilter.filter(body.content().trim()));
            ps.setString(5, status);
            ps.setLong(6, now);
            return ps;
        }, keyHolder);
        Long id = keyHolder.getKey() == null ? -1L : keyHolder.getKey().longValue();
        notifyMentions(body.content(), me, "帖子「" + body.title().trim() + "」中提到了你", id, 0);
        auditService.log("forum_thread_submit", me, "#" + id,
                status + " | " + body.title().trim());
        if (moderation) {
            pushService.pushEvent("forum_moderate", Map.of("kind", "thread", "id", String.valueOf(id), "username", me));
        }
        return ResponseEntity.ok(ApiResponse.success(
                moderation ? "已提交,审核通过后展示" : "发布成功", Map.of("id", id, "status", status)));
    }

    @PostMapping("/thread/{id}/reply")
    public ResponseEntity<Object> reply(@PathVariable long id, @RequestBody ReplyBody body,
                                        HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        if (body.content() == null || body.content().isBlank()) {
            return badRequest("内容必填");
        }
        if (body.content().length() > 10_000) {
            return badRequest("内容超限");
        }
        var rows = jdbc.queryForList("SELECT id, username, status, locked, title FROM dp_forum_thread WHERE id = ?", id);
        if (rows.isEmpty()) {
            return badRequest("帖子不存在");
        }
        Map<String, Object> t = rows.get(0);
        boolean admin = isAdminOfSite(request);
        if (!"published".equals(String.valueOf(t.get("status"))) && !admin
                && !me.equalsIgnoreCase(String.valueOf(t.get("username")))) {
            return badRequest("帖子不存在");
        }
        if (((Number) t.get("locked")).intValue() != 0 && !admin) {
            return badRequest("帖子已锁定,无法回复");
        }
        if (!rateLimiter.allowWindow("forum-reply:" + me.toLowerCase(), 1, 30_000)) {
            return badRequest("回复太频繁,请 30 秒后再试");
        }
        if (!rateLimiter.allowDaily("forum-reply-daily:" + me.toLowerCase(), 10)) {
            return badRequest("今天回复已达上限(10 条)");
        }
        boolean moderation = forumConfig().getOrDefault("moderation", Boolean.FALSE) instanceof Boolean b && b;
        String status = moderation ? "pending" : "published";
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO dp_forum_reply (thread_id, username, content, status, created_at) VALUES (?, ?, ?, ?, ?)",
                id, me, sensitiveWordFilter.filter(body.content().trim()), status, now);
        if (!moderation) {
            jdbc.update("UPDATE dp_forum_thread SET reply_count = reply_count + 1, last_reply_at = ? WHERE id = ?", now, id);
        }
        String author = String.valueOf(t.get("username"));
        if (!me.equalsIgnoreCase(author) && !"pending".equals(status)) {
            notificationRepository.save(new NotificationRecord(null, author, "forum_reply",
                    "帖子有了新回复", "你的帖子「" + t.get("title") + "」有新回复", null, null, me));
        }
        notifyMentions(body.content(), me, "帖子「" + t.get("title") + "」的回复中提到了你", id, 0);
        auditService.log("forum_reply_submit", me, "#" + id, status + " | 回复:" + t.get("title"));
        if (moderation) {
            pushService.pushEvent("forum_moderate", Map.of("kind", "reply", "id", String.valueOf(id), "username", me));
        }
        return ResponseEntity.ok(ApiResponse.success(moderation ? "已提交,审核通过后展示" : "回复成功"));
    }

    /** 编辑自己帖子(10 分钟内,留「已编辑」痕) */
    public record EditBody(String title, String content) {
    }

    @PostMapping("/thread/{id}/edit")
    public ResponseEntity<Object> edit(@PathVariable long id, @RequestBody EditBody body,
                                       HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        var rows = jdbc.queryForList("SELECT username, created_at, title FROM dp_forum_thread WHERE id = ?", id);
        if (rows.isEmpty()) {
            return badRequest("帖子不存在");
        }
        Map<String, Object> t = rows.get(0);
        if (!me.equalsIgnoreCase(String.valueOf(t.get("username"))) && !isAdminOfSite(request)) {
            return forbidden();
        }
        if (!isAdminOfSite(request)
                && System.currentTimeMillis() - toLong(t.get("created_at")) > EDIT_WINDOW_MS) {
            return badRequest("仅可编辑发布 10 分钟内的帖子");
        }
        if (body.title() == null || body.title().isBlank() || body.content() == null || body.content().isBlank()) {
            return badRequest("标题/内容必填");
        }
        jdbc.update("UPDATE dp_forum_thread SET title = ?, content = ?, edited = 1, updated_at = ? WHERE id = ?",
                sensitiveWordFilter.filter(body.title().trim()),
                sensitiveWordFilter.filter(body.content().trim()), System.currentTimeMillis(), id);
        auditService.log("forum_thread_edit", me, "#" + id, "编辑帖子: " + t.get("title"));
        return ResponseEntity.ok(ApiResponse.success("已保存"));
    }

    /** 点赞/取消(帖子或回复),like_enabled 开关 */
    @PostMapping("/like/{type}/{id}")
    public ResponseEntity<Object> like(@PathVariable String type, @PathVariable long id,
                                       HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        if (!Set.of("thread", "reply").contains(type)) {
            return badRequest("未知类型");
        }
        if (forumConfig().getOrDefault("likeEnabled", Boolean.TRUE) instanceof Boolean b && !b) {
            return badRequest("点赞功能未开启");
        }
        String table = "thread".equals(type) ? "dp_forum_thread" : "dp_forum_reply";
        var rows = jdbc.queryForList("SELECT id, username FROM " + table + " WHERE id = ?", id);
        if (rows.isEmpty()) {
            return badRequest("目标不存在");
        }
        String owner = String.valueOf(rows.get(0).get("username"));
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dp_forum_like WHERE target_type = ? AND target_id = ? AND username = ?",
                Integer.class, type, id, me);
        boolean liked;
        if (existing != null && existing > 0) {
            jdbc.update("DELETE FROM dp_forum_like WHERE target_type = ? AND target_id = ? AND username = ?",
                    type, id, me);
            jdbc.update("UPDATE " + table + " SET like_count = GREATEST(like_count - 1, 0) WHERE id = ?", id);
            liked = false;
        } else {
            jdbc.update("INSERT INTO dp_forum_like (target_type, target_id, username, created_at) VALUES (?, ?, ?, ?)",
                    type, id, me, System.currentTimeMillis());
            jdbc.update("UPDATE " + table + " SET like_count = like_count + 1 WHERE id = ?", id);
            liked = true;
            if (!me.equalsIgnoreCase(owner)) {
                notificationRepository.save(new NotificationRecord(null, owner, "forum_like",
                        "收到点赞", type.equals("thread") ? "你的帖子收到一个赞" : "你的回复收到一个赞", null, null, me));
            }
        }
        Integer count = jdbc.queryForObject(
                "SELECT like_count FROM " + table + " WHERE id = ?", Integer.class, id);
        return ResponseEntity.ok(ApiResponse.success(liked ? "已点赞" : "已取消",
                Map.of("liked", liked, "likes", count == null ? 0 : count)));
    }

    // ---------- 管理端 ----------

    /** 论坛开关(moderation=先审后发, likeEnabled=点赞),后台可切换 */
    @GetMapping("/admin/config")
    public ResponseEntity<Object> getConfig(HttpServletRequest request) {
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        return ResponseEntity.ok(Map.of("success", true, "data", forumConfig()));
    }

    @PutMapping("/admin/config")
    public ResponseEntity<Object> saveConfig(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        Map<String, Object> cfg = new LinkedHashMap<>(forumConfig());
        cfg.put("moderation", Boolean.TRUE.equals(body.get("moderation")));
        cfg.put("likeEnabled", body.get("likeEnabled") == null || Boolean.TRUE.equals(body.get("likeEnabled")));
        settingService.set("forum.config", cfg);
        auditService.log("settings_forum", me, null, "论坛设置: moderation=" + cfg.get("moderation"));
        return ResponseEntity.ok(ApiResponse.success("论坛设置已保存"));
    }

    @GetMapping("/admin/pending")
    public ResponseEntity<Object> adminPending(HttpServletRequest request) {
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "threads", jdbc.queryForList("SELECT id, section_id, username, title, content, status, created_at "
                        + "FROM dp_forum_thread WHERE status = 'pending' ORDER BY id ASC LIMIT 200"),
                "replies", jdbc.queryForList("SELECT r.id, r.thread_id, r.username, r.content, r.created_at, "
                        + "t.title AS thread_title FROM dp_forum_reply r JOIN dp_forum_thread t ON t.id = r.thread_id "
                        + "WHERE r.status = 'pending' ORDER BY r.id ASC LIMIT 200"))));
    }

    @PostMapping("/admin/thread/{id}/{action}")
    public ResponseEntity<Object> moderateThread(@PathVariable long id, @PathVariable String action,
                                                 HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        var rows = jdbc.queryForList("SELECT id, username, title, status FROM dp_forum_thread WHERE id = ?", id);
        if (rows.isEmpty()) {
            return badRequest("帖子不存在");
        }
        Map<String, Object> t = rows.get(0);
        String author = String.valueOf(t.get("username"));
        long now = System.currentTimeMillis();
        switch (action) {
            case "approve" -> {
                if (!"published".equals(String.valueOf(t.get("status")))) {
                    jdbc.update("UPDATE dp_forum_thread SET status = 'published', reviewed_by = ?, reviewed_at = ? WHERE id = ?", me, now, id);
                    jdbc.update("UPDATE dp_forum_thread SET last_reply_at = ? WHERE id = ? AND last_reply_at IS NULL", now, id);
                    notificationRepository.save(new NotificationRecord(null, author, "forum_thread_approved",
                            "帖子审核通过", "你的帖子「" + t.get("title") + "」已通过审核并展示", null, null, me));
                }
            }
            case "reject" -> {
                jdbc.update("UPDATE dp_forum_thread SET status = 'rejected', reviewed_by = ?, reviewed_at = ? WHERE id = ?", me, now, id);
                notificationRepository.save(new NotificationRecord(null, author, "forum_thread_rejected",
                        "帖子未通过审核", "你的帖子「" + t.get("title") + "」未通过审核,如有疑问请联系管理员", null, null, me));
            }
            case "delete" -> jdbc.update("UPDATE dp_forum_thread SET status = 'deleted' WHERE id = ?", id);
            case "pin" -> jdbc.update("UPDATE dp_forum_thread SET pinned = 1 WHERE id = ?", id);
            case "unpin" -> jdbc.update("UPDATE dp_forum_thread SET pinned = 0 WHERE id = ?", id);
            case "essence" -> jdbc.update("UPDATE dp_forum_thread SET essence = 1 WHERE id = ?", id);
            case "unessence" -> jdbc.update("UPDATE dp_forum_thread SET essence = 0 WHERE id = ?", id);
            case "lock" -> jdbc.update("UPDATE dp_forum_thread SET locked = 1 WHERE id = ?", id);
            case "unlock" -> jdbc.update("UPDATE dp_forum_thread SET locked = 0 WHERE id = ?", id);
            default -> {
                return badRequest("未知操作");
            }
        }
        auditService.log("forum_thread_" + action, me, "#" + id, String.valueOf(t.get("title")));
        pushService.pushEvent("forum_moderate", Map.of("kind", "thread", "action", action, "id", String.valueOf(id)));
        return ResponseEntity.ok(ApiResponse.success("已处理"));
    }

    @PostMapping("/admin/reply/{id}/{action}")
    public ResponseEntity<Object> moderateReply(@PathVariable long id, @PathVariable String action,
                                                HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        var rows = jdbc.queryForList("SELECT id, thread_id, username, status FROM dp_forum_reply WHERE id = ?", id);
        if (rows.isEmpty()) {
            return badRequest("回复不存在");
        }
        Map<String, Object> r = rows.get(0);
        long threadId = toLong(r.get("thread_id"));
        if ("approve".equals(action)) {
            if ("pending".equals(String.valueOf(r.get("status")))) {
                jdbc.update("UPDATE dp_forum_reply SET status = 'published' WHERE id = ?", id);
                jdbc.update("UPDATE dp_forum_thread SET reply_count = reply_count + 1, last_reply_at = ? WHERE id = ?",
                        System.currentTimeMillis(), threadId);
            }
        } else if ("delete".equals(action)) {
            if ("published".equals(String.valueOf(r.get("status")))) {
                jdbc.update("UPDATE dp_forum_thread SET reply_count = GREATEST(reply_count - 1, 0) WHERE id = ?", threadId);
            }
            jdbc.update("UPDATE dp_forum_reply SET status = 'deleted' WHERE id = ?", id);
        } else {
            return badRequest("未知操作");
        }
        auditService.log("forum_reply_" + action, me, "#" + id, "回复审核");
        pushService.pushEvent("forum_moderate", Map.of("kind", "reply", "action", action, "id", String.valueOf(id)));
        return ResponseEntity.ok(ApiResponse.success("已处理"));
    }

    public record SectionBody(Long id, String name, String description, Integer sort, Boolean locked) {
    }

    @PostMapping("/admin/section")
    public ResponseEntity<Object> saveSection(@RequestBody SectionBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        if (body.name() == null || body.name().isBlank()) {
            return badRequest("板块名必填");
        }
        if (body.id() == null) {
            jdbc.update("INSERT INTO dp_forum_section (name, description, sort, locked, created_at) VALUES (?, ?, ?, ?, ?)",
                    body.name().trim(), body.description(), body.sort() == null ? 0 : body.sort(),
                    Boolean.TRUE.equals(body.locked()) ? 1 : 0, System.currentTimeMillis());
            auditService.log("forum_section_create", me, body.name().trim(), null);
            return ResponseEntity.ok(ApiResponse.success("板块已创建"));
        }
        jdbc.update("UPDATE dp_forum_section SET name = ?, description = ?, sort = ?, locked = ? WHERE id = ?",
                body.name().trim(), body.description(), body.sort() == null ? 0 : body.sort(),
                Boolean.TRUE.equals(body.locked()) ? 1 : 0, body.id());
        auditService.log("forum_section_update", me, "#" + body.id(), body.name().trim());
        return ResponseEntity.ok(ApiResponse.success("板块已保存"));
    }

    @DeleteMapping("/admin/section/{id}")
    public ResponseEntity<Object> deleteSection(@PathVariable long id, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        Integer threads = jdbc.queryForObject("SELECT COUNT(*) FROM dp_forum_thread WHERE section_id = ? AND status != 'deleted'",
                Integer.class, id);
        if (threads != null && threads > 0) {
            return badRequest("板块下还有 " + threads + " 个帖子,先迁移或删除后再操作");
        }
        jdbc.update("DELETE FROM dp_forum_section WHERE id = ?", id);
        auditService.log("forum_section_delete", me, "#" + id, null);
        return ResponseEntity.ok(ApiResponse.success("板块已删除"));
    }

    // ---------- 工具 ----------

    private void notifyMentions(String content, String fromUser, String message, long threadId, long replyId) {
        if (content == null || content.isBlank()) {
            return;
        }
        Matcher m = MENTION.matcher(content);
        java.util.Set<String> mentioned = new java.util.HashSet<>();
        while (m.find() && mentioned.size() <= 5) {
            String name = m.group(1);
            if (name.equalsIgnoreCase(fromUser) || !mentioned.add(name)) {
                continue;
            }
            if (userRepository.findByUsernameIgnoreCase(name).isEmpty()) {
                continue;
            }
            notificationRepository.save(new NotificationRecord(null, name, "forum_mention",
                    "收到 @ 提及", message, null, null, fromUser));
        }
    }

    private Map<String, Object> forumConfig() {
        Map<String, Object> cfg = settingService.getMap("forum.config");
        return cfg == null ? Map.of() : cfg;
    }

    private Object[] appendArgs(Object[] base, Object... extra) {
        Object[] all = new Object[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }

    private Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
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
