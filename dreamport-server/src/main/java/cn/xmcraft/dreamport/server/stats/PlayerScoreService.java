package cn.xmcraft.dreamport.server.stats;

import cn.xmcraft.dreamport.server.settings.SettingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 玩家综合评分(行为分析数据产品):
 * 五项指标(活跃天数/在线时长/累计积分/注册天数/社区参与)各自按封顶值归一化后加权求和,
 * 满分 100。权重与封顶值存 dp_setting analytics.config,后台可调;publicScore 控制对玩家公开。
 * 社区参与 = 论坛发帖+回帖(published)+投票+反馈+点赞 计数。
 */
@Service
public class PlayerScoreService {

    public record ScoreResult(String username, double score,
                              int activeDays, double playtimeHours, int pointsTotal,
                              int registerDays, int communityCount) {
    }

    /** 默认权重(总 100)与封顶值 */
    private static Map<String, Object> defaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activeDaysW", 25);
        m.put("playtimeHoursW", 25);
        m.put("pointsW", 20);
        m.put("registerDaysW", 15);
        m.put("communityW", 15);
        m.put("activeDaysCap", 100);
        m.put("playtimeHoursCap", 500);
        m.put("pointsCap", 5000);
        m.put("registerDaysCap", 365);
        m.put("communityCap", 100);
        m.put("publicScore", true);
        return m;
    }

    private final JdbcTemplate jdbc;
    private final SettingService settingService;

    public PlayerScoreService(JdbcTemplate jdbc, SettingService settingService) {
        this.jdbc = jdbc;
        this.settingService = settingService;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> config() {
        Map<String, Object> cfg = settingService.getMap(SettingService.KEY_ANALYTICS_CONFIG);
        Map<String, Object> merged = new LinkedHashMap<>(defaults());
        if (cfg != null) {
            merged.putAll(cfg);
        }
        return merged;
    }

    public boolean isPublic() {
        return Boolean.TRUE.equals(config().get("publicScore"))
                || "true".equalsIgnoreCase(String.valueOf(config().get("publicScore")));
    }

    private int num(Map<String, Object> cfg, String key) {
        Object v = cfg.get(key);
        return v == null ? 0 : (int) Math.round(Double.parseDouble(String.valueOf(v)));
    }

    /** 单玩家评分与分项指标 */
    public ScoreResult scoreOf(String username) {
        Map<String, Object> cfg = config();
        var userOpt = jdbc.queryForList("SELECT minecraft_name, reg_time FROM dp_user WHERE username = ?", username);
        String gameName = username;
        long regTime = 0;
        if (!userOpt.isEmpty()) {
            Object mn = userOpt.get(0).get("minecraft_name");
            if (mn != null && !String.valueOf(mn).isBlank()) gameName = String.valueOf(mn);
            Object rt = userOpt.get(0).get("reg_time");
            regTime = rt == null ? 0 : ((Number) rt).longValue();
        }

        Integer activeDays = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT activity_date) FROM dp_daily_activity WHERE username IN (?, ?)",
                Integer.class, username, gameName);
        Long playtimeSeconds = jdbc.queryForObject(
                "SELECT COALESCE(SUM(playtime_seconds),0) FROM dp_daily_activity WHERE username IN (?, ?)",
                Long.class, username, gameName);
        Integer points = jdbc.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN delta > 0 THEN delta ELSE 0 END),0) FROM dp_points_ledger WHERE username = ?",
                Integer.class, username);
        int registerDays = regTime == 0 ? 0 : (int) ((System.currentTimeMillis() - regTime) / 86_400_000L);
        Integer community = jdbc.queryForObject(
                "SELECT (SELECT COUNT(*) FROM dp_forum_thread WHERE username = ? AND status = 'published')"
                        + " + (SELECT COUNT(*) FROM dp_forum_reply WHERE username = ? AND status = 'published')"
                        + " + (SELECT COUNT(*) FROM dp_poll_vote WHERE username = ?)"
                        + " + (SELECT COUNT(*) FROM dp_feedback WHERE username = ?)"
                        + " + (SELECT COUNT(*) FROM dp_forum_like WHERE username = ?)",
                Integer.class, username, username, username, username, username);

        int ad = activeDays == null ? 0 : activeDays;
        double ph = (playtimeSeconds == null ? 0 : playtimeSeconds) / 3600.0;
        int pt = points == null ? 0 : points;
        int cc = community == null ? 0 : community;

        double score = norm(ad, num(cfg, "activeDaysCap")) * num(cfg, "activeDaysW")
                + norm(ph, num(cfg, "playtimeHoursCap")) * num(cfg, "playtimeHoursW")
                + norm(pt, num(cfg, "pointsCap")) * num(cfg, "pointsW")
                + norm(registerDays, num(cfg, "registerDaysCap")) * num(cfg, "registerDaysW")
                + norm(cc, num(cfg, "communityCap")) * num(cfg, "communityW");
        score = Math.round(score * 10) / 10.0;

        return new ScoreResult(username, score, ad, Math.round(ph * 10) / 10.0, pt, registerDays, cc);
    }

    private double norm(double value, int cap) {
        return cap <= 0 ? 0 : Math.min(value / (double) cap, 1.0);
    }

    /** 评分排行榜(已通过白名单的玩家,降序) */
    public List<ScoreResult> topScores(int limit) {
        List<String> users = jdbc.queryForList(
                "SELECT username FROM dp_user WHERE status = 'approved'", String.class);
        List<ScoreResult> all = new ArrayList<>();
        for (String u : users) {
            try {
                all.add(scoreOf(u));
            } catch (Exception ignored) {
            }
        }
        all.sort((a, b) -> Double.compare(b.score(), a.score()));
        return all.subList(0, Math.min(limit, all.size()));
    }
}
