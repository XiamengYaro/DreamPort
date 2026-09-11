package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.stats.PlayerScoreService;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行为分析数据端点:
 * - GET /api/leaderboard/score:综合评分榜(publicScore 开关控制,关闭时 403)
 * - GET /api/user/score:本人评分(JWT,不受公开开关限制)
 * - GET /api/admin/analytics/overview?days=30:管理端行为分析(DAU/聊天量/注册趋势/Top 活跃)
 */
@RestController
@RequestMapping("/api")
public class AnalyticsController {

    private final PlayerScoreService scoreService;
    private final JdbcTemplate jdbc;

    public AnalyticsController(PlayerScoreService scoreService, JdbcTemplate jdbc) {
        this.scoreService = scoreService;
        this.jdbc = jdbc;
    }

    @GetMapping("/leaderboard/score")
    public ResponseEntity<Object> scoreLeaderboard(@RequestParam(defaultValue = "50") int limit) {
        if (!scoreService.isPublic()) {
            return ResponseEntity.status(403).body(ApiResponse.failure("评分榜未公开"));
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (var r : scoreService.topScores(Math.max(1, Math.min(limit, 100)))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", r.username());
            m.put("score", r.score());
            m.put("activeDays", r.activeDays());
            m.put("playtimeHours", r.playtimeHours());
            m.put("pointsTotal", r.pointsTotal());
            m.put("registerDays", r.registerDays());
            m.put("communityCount", r.communityCount());
            list.add(m);
        }
        return ResponseEntity.ok(ApiResponse.success(null, Map.of("list", list)));
    }

    @GetMapping("/user/score")
    public ResponseEntity<Object> myScore(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        var r = scoreService.scoreOf(me);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("score", r.score());
        data.put("activeDays", r.activeDays());
        data.put("playtimeHours", r.playtimeHours());
        data.put("pointsTotal", r.pointsTotal());
        data.put("registerDays", r.registerDays());
        data.put("communityCount", r.communityCount());
        return ResponseEntity.ok(ApiResponse.success(null, data));
    }

    /** 行为分析看板(管理员):DAU/聊天量/注册趋势 + Top 活跃玩家 */
    @GetMapping("/admin/analytics/overview")
    public ResponseEntity<Object> analyticsOverview(@RequestParam(defaultValue = "30") int days,
                                                    HttpServletRequest request) {
        if (AuthUtil.currentUser(request) == null || !AuthUtil.isAdmin(request)) {
            return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
        }
        int d = Math.max(7, Math.min(days, 90));
        long since = System.currentTimeMillis() - d * 86_400_000L;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", d);

        // DAU 趋势(每日活跃玩家数)
        data.put("dau", jdbc.queryForList(
                "SELECT activity_date AS t, COUNT(DISTINCT username) AS v FROM dp_daily_activity "
                        + "WHERE activity_date >= ? GROUP BY activity_date ORDER BY activity_date",
                java.time.LocalDate.now().minusDays(d).toString()));

        // 聊天量趋势(每日条数;聊天仅 7 天保留,返回实际窗口)
        data.put("chat", jdbc.queryForList(
                "SELECT DATE_FORMAT(FROM_UNIXTIME(created_at / 1000), '%Y-%m-%d') AS t, COUNT(*) AS v "
                        + "FROM dp_chat_message WHERE created_at >= ? GROUP BY t ORDER BY t",
                System.currentTimeMillis() - 7L * 86_400_000));

        // 注册趋势(每日新增)
        data.put("registrations", jdbc.queryForList(
                "SELECT DATE_FORMAT(FROM_UNIXTIME(reg_time / 1000), '%Y-%m-%d') AS t, COUNT(*) AS v "
                        + "FROM dp_user WHERE reg_time >= ? GROUP BY t ORDER BY t",
                since));

        // Top 活跃玩家(近 d 天在线时长)
        data.put("topActive", jdbc.queryForList(
                "SELECT username, SUM(playtime_seconds) / 3600 AS hours, COUNT(*) AS activeDays "
                        + "FROM dp_daily_activity WHERE activity_date >= ? "
                        + "GROUP BY username ORDER BY hours DESC LIMIT 10",
                java.time.LocalDate.now().minusDays(d).toString()));

        // 汇总
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("totalPlaytimeHours", jdbc.queryForObject(
                "SELECT COALESCE(SUM(playtime_seconds),0) / 3600 FROM dp_daily_activity WHERE activity_date >= ?",
                Double.class, java.time.LocalDate.now().minusDays(d).toString()));
        totals.put("activePlayers", jdbc.queryForObject(
                "SELECT COUNT(DISTINCT username) FROM dp_daily_activity WHERE activity_date >= ?",
                Integer.class, java.time.LocalDate.now().minusDays(d).toString()));
        totals.put("avgHoursPerPlayer", jdbc.queryForObject(
                "SELECT COALESCE(AVG(h),0) FROM (SELECT username, SUM(playtime_seconds)/3600 AS h "
                        + "FROM dp_daily_activity WHERE activity_date >= ? GROUP BY username) t",
                Double.class, java.time.LocalDate.now().minusDays(d).toString()));
        data.put("totals", totals);
        return ResponseEntity.ok(ApiResponse.success(null, data));
    }
}
