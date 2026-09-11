package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.user.UserRepository;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 投票:后台创建/开启/关闭,玩家投票与查看结果。
 * 结果可见性逐场可配(result_visibility):open=实时可见(默认) / ended=投票后或结束后可见;管理员始终可见。
 */
@RestController
@RequestMapping("/api/polls")
public class PollController {

    private final JdbcTemplate jdbc;
    private final SettingService settingService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public PollController(JdbcTemplate jdbc, SettingService settingService, UserRepository userRepository,
                          AuditService auditService) {
        this.jdbc = jdbc;
        this.settingService = settingService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public record CreateBody(String title, String description, Boolean multiple,
                             String resultVisibility, Long endsAt, List<String> options) {
    }

    /** 玩家:进行中 + 已结束的投票列表(含我的选择与可见范围内的结果) */
    @GetMapping
    public ResponseEntity<Object> list(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        boolean admin = me != null && isAdminOfSite(request);
        if (me == null) {
            // 游客:仅展示进行中投票的题面(不能看结果、不能投)
            return ResponseEntity.ok(Map.of("success", true,
                    "data", visiblePolls(null, false)));
        }
        return ResponseEntity.ok(Map.of("success", true, "data", visiblePolls(me, admin)));
    }

    /** 玩家投票(单选传一个 optionId,多选传多个) */
    public record VoteBody(List<Long> optionIds) {
    }

    @PostMapping("/{id}/vote")
    public ResponseEntity<Object> vote(@PathVariable long id, @RequestBody VoteBody body,
                                       HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        Map<String, Object> poll = findPoll(id);
        if (poll == null || !"open".equals(String.valueOf(poll.get("status")))) {
            return badRequest("投票不存在或已结束");
        }
        Long endsAt = toLong(poll.get("ends_at"));
        if (endsAt != null && endsAt > 0 && System.currentTimeMillis() > endsAt) {
            return badRequest("投票已到截止时间");
        }
        boolean multiple = ((Number) poll.get("multiple")).intValue() != 0;
        List<Long> optionIds = body.optionIds() == null ? List.of() : body.optionIds().stream()
                .distinct().toList();
        if (optionIds.isEmpty()) {
            return badRequest("请选择至少一个选项");
        }
        if (!multiple && optionIds.size() > 1) {
            return badRequest("该投票为单选");
        }
        // 选项必须属于本场投票
        Set<Long> valid = new HashSet<>(jdbc.queryForList(
                "SELECT id FROM dp_poll_option WHERE poll_id = ?", Long.class, id));
        if (!valid.containsAll(optionIds)) {
            return badRequest("选项无效");
        }
        // 统一先删后插:单选自然一行;多选写入全部所选行(uk: poll_id+username+option_id)
        long now = System.currentTimeMillis();
        jdbc.update("DELETE FROM dp_poll_vote WHERE poll_id = ? AND username = ?", id, me);
        for (Long oid : optionIds) {
            jdbc.update("INSERT INTO dp_poll_vote (poll_id, option_id, username, created_at) VALUES (?, ?, ?, ?)",
                    id, oid, me, now);
        }
        return ResponseEntity.ok(ApiResponse.success("投票成功"));
    }

    // ---------- 管理端 ----------

    @PostMapping("/admin")
    public ResponseEntity<Object> create(@RequestBody CreateBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        if (body.title() == null || body.title().isBlank()
                || body.options() == null || body.options().size() < 2) {
            return badRequest("标题与至少 2 个选项必填");
        }
        long now = System.currentTimeMillis();
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO dp_poll (title, description, multiple, result_visibility, status, ends_at, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, 'draft', ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, body.title().trim());
            ps.setString(2, body.description() == null ? "" : body.description().trim());
            ps.setInt(3, Boolean.TRUE.equals(body.multiple()) ? 1 : 0);
            ps.setString(4, "ended".equals(body.resultVisibility()) ? "ended" : "open");
            if (body.endsAt() == null) {
                ps.setNull(5, java.sql.Types.BIGINT);
            } else {
                ps.setLong(5, body.endsAt());
            }
            ps.setString(6, me);
            ps.setLong(7, now);
            return ps;
        }, keyHolder);
        Long id = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        if (id == null) {
            return ResponseEntity.internalServerError().body(ApiResponse.failure("投票创建失败"));
        }
        saveOptions(id, body.options());
        auditService.log("poll_create", me, "#" + id, body.title());
        return ResponseEntity.ok(ApiResponse.success("投票已创建(草稿)", Map.of("id", id)));
    }

    @PostMapping("/{id}/admin/{action}")
    public ResponseEntity<Object> moderate(@PathVariable long id, @PathVariable String action,
                                           HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        Map<String, Object> poll = findPoll(id);
        if (poll == null) {
            return badRequest("投票不存在");
        }
        switch (action) {
            case "open" -> jdbc.update("UPDATE dp_poll SET status = 'open' WHERE id = ?", id);
            case "close" -> jdbc.update("UPDATE dp_poll SET status = 'closed' WHERE id = ?", id);
            case "delete" -> {
                jdbc.update("DELETE FROM dp_poll_vote WHERE poll_id = ?", id);
                jdbc.update("DELETE FROM dp_poll_option WHERE poll_id = ?", id);
                jdbc.update("DELETE FROM dp_poll WHERE id = ?", id);
            }
            default -> {
                return badRequest("未知操作");
            }
        }
        auditService.log("poll_" + action, me, "#" + id, String.valueOf(poll.get("title")));
        if ("open".equals(action)) {
        }
        return ResponseEntity.ok(ApiResponse.success("已" + switch (action) {
            case "open" -> "开启";
            case "close" -> "关闭";
            default -> "删除";
        }));
    }

    public record UpdateBody(String title, String description, List<String> options) {
    }

    @PostMapping("/{id}/admin/update")
    public ResponseEntity<Object> update(@PathVariable long id, @RequestBody UpdateBody body,
                                         HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        Map<String, Object> poll = findPoll(id);
        if (poll == null) {
            return badRequest("投票不存在");
        }
        jdbc.update("UPDATE dp_poll SET title = ?, description = ? WHERE id = ?",
                body.title() == null ? poll.get("title") : body.title().trim(),
                body.description() == null ? poll.get("description") : body.description().trim(), id);
        if (body.options() != null && body.options().size() >= 2) {
            jdbc.update("DELETE FROM dp_poll_option WHERE poll_id = ?", id);
            saveOptions(id, body.options());
            jdbc.update("DELETE FROM dp_poll_vote WHERE poll_id = ?", id);
        }
        auditService.log("poll_update", me, "#" + id, String.valueOf(poll.get("title")));
        return ResponseEntity.ok(ApiResponse.success("已保存(改动选项会清空已有票)"));
    }

    @GetMapping("/admin/list")
    public ResponseEntity<Object> adminList(HttpServletRequest request) {
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        List<Map<String, Object>> polls = jdbc.queryForList(
                "SELECT id, title, description, multiple, result_visibility, status, ends_at, created_by, created_at "
                        + "FROM dp_poll ORDER BY id DESC");
        for (Map<String, Object> p : polls) {
            p.put("options", jdbc.queryForList(
                    "SELECT id, label, sort, (SELECT COUNT(*) FROM dp_poll_vote v WHERE v.option_id = dp_poll_option.id) AS votes "
                            + "FROM dp_poll_option WHERE poll_id = ? ORDER BY sort, id", toLong(p.get("id"))));
            p.put("totalVotes", jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT username) FROM dp_poll_vote WHERE poll_id = ?",
                    Integer.class, toLong(p.get("id"))));
        }
        return ResponseEntity.ok(Map.of("success", true, "data", polls));
    }

    // ---------- 内部 ----------

    private List<Map<String, Object>> visiblePolls(String me, boolean admin) {
        List<Map<String, Object>> polls = jdbc.queryForList(
                admin
                        ? "SELECT id, title, description, multiple, result_visibility, status, ends_at, created_by, created_at FROM dp_poll ORDER BY id DESC"
                        : "SELECT id, title, description, multiple, result_visibility, status, ends_at, created_by, created_at FROM dp_poll "
                        + "WHERE status IN ('open', 'closed') ORDER BY id DESC");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> p : polls) {
            long id = toLong(p.get("id"));
            boolean ended = "closed".equals(String.valueOf(p.get("status")))
                    || (toLong(p.get("ends_at")) != null && toLong(p.get("ends_at")) > 0
                    && System.currentTimeMillis() > toLong(p.get("ends_at")));
            List<Map<String, Object>> options = jdbc.queryForList(
                    "SELECT id, label, sort, (SELECT COUNT(*) FROM dp_poll_vote v WHERE v.option_id = dp_poll_option.id) AS votes "
                            + "FROM dp_poll_option WHERE poll_id = ? ORDER BY sort, id", id);
            int totalVotes = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT username) FROM dp_poll_vote WHERE poll_id = ?", Integer.class, id);

            Set<Long> myOptionIds = new HashSet<>();
            boolean voted = false;
            if (me != null) {
                for (var row : jdbc.queryForList(
                        "SELECT option_id FROM dp_poll_vote WHERE poll_id = ? AND username = ?", id, me)) {
                    myOptionIds.add(((Number) row.get("option_id")).longValue());
                    voted = true;
                }
            }
            boolean showResults = admin || "open".equals(String.valueOf(p.get("result_visibility")))
                    || (me != null && voted) || ended;
            List<Map<String, Object>> outOptions = new ArrayList<>();
            for (var o : options) {
                Map<String, Object> oo = new LinkedHashMap<>();
                long oid = toLong(o.get("id"));
                oo.put("id", oid);
                oo.put("label", o.get("label"));
                oo.put("votes", showResults ? toLong(o.get("votes")) : null);
                oo.put("myChoice", myOptionIds.contains(oid));
                outOptions.add(oo);
            }
            Map<String, Object> out = new LinkedHashMap<>(p);
            out.put("ended", ended);
            out.put("options", outOptions);
            out.put("totalVotes", showResults ? totalVotes : null);
            out.put("voted", voted);
            out.put("showResults", showResults);
            result.add(out);
        }
        return result;
    }

    private void saveOptions(Long pollId, List<String> options) {
        int sort = 0;
        for (String label : options) {
            if (label == null || label.isBlank()) {
                continue;
            }
            jdbc.update("INSERT INTO dp_poll_option (poll_id, label, sort) VALUES (?, ?, ?)",
                    pollId, label.trim(), sort++);
        }
    }

    private Map<String, Object> findPoll(long id) {
        var rows = jdbc.queryForList("SELECT id, title, description, multiple, result_visibility, status, ends_at, created_at "
                + "FROM dp_poll WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private record UserRecordLight(String username) {
    }

    private List<UserRecordLight> approvedUsers() {
        return jdbc.queryForList("SELECT username FROM dp_user WHERE status = 'approved'")
                .stream().map(r -> new UserRecordLight(String.valueOf(r.get("username")))).toList();
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
