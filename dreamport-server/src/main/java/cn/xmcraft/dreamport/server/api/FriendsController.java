package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 好友系统(基础+在线标记):
 * 申请 → 对方同意 → 好友;拒绝/删除 = 删行。在线标记由前端比对现有心跳玩家名单。
 * 双向去重:发起前检查正向/反向已有关系(含 pending)。
 */
@RestController
@RequestMapping("/api/friends")
public class FriendsController {

    private final JdbcTemplate jdbc;
    private final cn.xmcraft.dreamport.server.user.UserRepository userRepository;

    public FriendsController(JdbcTemplate jdbc, cn.xmcraft.dreamport.server.user.UserRepository userRepository) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
    }

    /** 好友行:me=当前用户视角的对方 */
    public record FriendRow(long id, String me, String other, String status, boolean outgoing, long createdAt) {
    }

    @PostMapping("/request/{username}")
    public ResponseEntity<Object> request(@PathVariable String username, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        String target = username == null ? "" : username.trim();
        if (target.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("用户名必填"));
        }
        if (target.equalsIgnoreCase(me)) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("不能添加自己"));
        }
        if (userRepository.findByUsernameIgnoreCase(target).isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("用户不存在"));
        }
        // 已有关系(正向或反向,含 pending)即拒绝重复
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dp_friendship WHERE (requester = ? AND addressee = ?) OR (requester = ? AND addressee = ?)",
                Integer.class, me, target, target, me);
        if (exists != null && exists > 0) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("你们已是好友或已有待处理的申请"));
        }
        jdbc.update("INSERT INTO dp_friendship (requester, addressee, status, created_at) VALUES (?, ?, 'pending', ?)",
                me, target, System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.success("好友申请已发送"));
    }

    /** 同意收到的申请(仅 addressee 可操作) */
    @PostMapping("/{id}/accept")
    public ResponseEntity<Object> accept(@PathVariable long id, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        int updated = jdbc.update(
                "UPDATE dp_friendship SET status = 'accepted', responded_at = ? WHERE id = ? AND addressee = ? AND status = 'pending'",
                System.currentTimeMillis(), id, me);
        if (updated == 0) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("申请不存在或已处理"));
        }
        return ResponseEntity.ok(ApiResponse.success("已同意,你们现在是好友了"));
    }

    /** 拒绝收到的申请(删行) */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Object> reject(@PathVariable long id, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        int updated = jdbc.update(
                "DELETE FROM dp_friendship WHERE id = ? AND addressee = ? AND status = 'pending'", id, me);
        if (updated == 0) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("申请不存在或已处理"));
        }
        return ResponseEntity.ok(ApiResponse.success("已拒绝"));
    }

    /** 删除好友(任一方可删) */
    @DeleteMapping("/{username}")
    public ResponseEntity<Object> remove(@PathVariable String username, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        jdbc.update("DELETE FROM dp_friendship WHERE (requester = ? AND addressee = ?) OR (requester = ? AND addressee = ?)",
                me, username, username, me);
        return ResponseEntity.ok(ApiResponse.success("已删除好友"));
    }

    /** 我的好友列表(accepted)+ 收到的待处理申请 */
    @GetMapping
    public ResponseEntity<Object> list(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        List<Map<String, Object>> friends = new ArrayList<>();
        for (var row : jdbc.queryForList(
                "SELECT id, requester, addressee, created_at, responded_at FROM dp_friendship "
                        + "WHERE status = 'accepted' AND (requester = ? OR addressee = ?) ORDER BY responded_at DESC",
                me, me)) {
            String other = String.valueOf(row.get("requester")).equalsIgnoreCase(me)
                    ? String.valueOf(row.get("addressee")) : String.valueOf(row.get("requester"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) row.get("id")).longValue());
            m.put("username", other);
            m.put("since", ((Number) row.get("responded_at")).longValue());
            friends.add(m);
        }

        List<Map<String, Object>> pendingIncoming = new ArrayList<>();
        for (var row : jdbc.queryForList(
                "SELECT id, requester, created_at FROM dp_friendship WHERE addressee = ? AND status = 'pending' ORDER BY created_at DESC",
                me)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) row.get("id")).longValue());
            m.put("username", String.valueOf(row.get("requester")));
            m.put("createdAt", ((Number) row.get("created_at")).longValue());
            pendingIncoming.add(m);
        }

        // 我发出的待处理(便于撤回)
        List<String> pendingOutgoing = new ArrayList<>();
        for (var row : jdbc.queryForList(
                "SELECT addressee FROM dp_friendship WHERE requester = ? AND status = 'pending'", me)) {
            pendingOutgoing.add(String.valueOf(row.get("addressee")));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("friends", friends);
        data.put("pendingIncoming", pendingIncoming);
        data.put("pendingOutgoing", pendingOutgoing);
        return ResponseEntity.ok(ApiResponse.success(null, data));
    }

    /** 指定玩家与我当前的好友状态(公开资料页「加好友」按钮的状态感知) */
    @GetMapping("/status/{username}")
    public ResponseEntity<Object> status(@PathVariable String username, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.ok(ApiResponse.success(null, Map.of("state", "guest")));
        }
        String target = username == null ? "" : username.trim();
        if (target.equalsIgnoreCase(me)) {
            return ResponseEntity.ok(ApiResponse.success(null, Map.of("state", "self")));
        }
        Integer accepted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dp_friendship WHERE status = 'accepted' AND "
                        + "((requester = ? AND addressee = ?) OR (requester = ? AND addressee = ?))",
                Integer.class, me, target, target, me);
        if (accepted != null && accepted > 0) {
            return ResponseEntity.ok(ApiResponse.success(null, Map.of("state", "friends")));
        }
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dp_friendship WHERE status = 'pending' AND "
                        + "((requester = ? AND addressee = ?) OR (requester = ? AND addressee = ?))",
                Integer.class, me, target, target, me);
        return ResponseEntity.ok(ApiResponse.success(null,
                Map.of("state", pending != null && pending > 0 ? "pending" : "none")));
    }
}
