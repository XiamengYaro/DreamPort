package cn.xmcraft.dreamport.server.titles;

import cn.xmcraft.dreamport.server.notification.NotificationRecord;
import cn.xmcraft.dreamport.server.notification.NotificationRepository;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 称号与成就系统:
 * - 称号定义存 dp_setting titles.config(JSON,后台可视化 CRUD 维护)
 * - 成就定义存 dp_setting achievements.config(JSON;指标:playtime_total 秒/register_days/invite_count/points_total)
 * - 达成 → 自动授予称号(dp_user_titles)+ 站内铃铛通知;佩戴记录 dp_title_equipped(每用户一条)
 */
@Service
public class TitleService {

    private final SettingService settingService;
    private final JdbcTemplate jdbcTemplate;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public TitleService(SettingService settingService, JdbcTemplate jdbcTemplate,
                        NotificationRepository notificationRepository, UserRepository userRepository) {
        this.settingService = settingService;
        this.jdbcTemplate = jdbcTemplate;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // ---------- 定义 ----------

    public record TitleDef(String code, String name, String desc, String color, boolean enabled) {}

    public record AchievementDef(String id, String name, String desc, String metric,
                                 int target, String reward, boolean enabled) {}

    private static final String DEFAULT_TITLES_JSON = """
            {"titles":[
              {"code":"rookie","name":"初来乍到","desc":"加入服务器的新人","color":"#a3e635","enabled":true},
              {"code":"regular","name":"常客","desc":"累计在线 10 小时","color":"#fbbf24","enabled":true},
              {"code":"veteran","name":"老玩家","desc":"累计在线 50 小时","color":"#fb923c","enabled":true},
              {"code":"loyal","name":"忠诚成员","desc":"注册满 30 天","color":"#60a5fa","enabled":true},
              {"code":"recruiter","name":"招募者","desc":"成功邀请 1 位玩家","color":"#c084fc","enabled":true},
              {"code":"diplomat","name":"外交官","desc":"成功邀请 5 位玩家","color":"#f472b6","enabled":true},
              {"code":"saver","name":"理财达人","desc":"累计获得 500 积分","color":"#34d399","enabled":true}
            ]}""";

    private static final String DEFAULT_ACHIEVEMENTS_JSON = """
            {"achievements":[
              {"id":"online_10h","name":"常客之路","desc":"累计在线满 10 小时","metric":"playtime_total","target":36000,"reward":"regular","enabled":true},
              {"id":"online_50h","name":"老玩家之路","desc":"累计在线满 50 小时","metric":"playtime_total","target":180000,"reward":"veteran","enabled":true},
              {"id":"register_30d","name":"忠诚之路","desc":"注册满 30 天","metric":"register_days","target":30,"reward":"loyal","enabled":true},
              {"id":"invite_1","name":"招募者","desc":"成功邀请 1 位玩家注册","metric":"invite_count","target":1,"reward":"recruiter","enabled":true},
              {"id":"invite_5","name":"外交官","desc":"成功邀请 5 位玩家注册","metric":"invite_count","target":5,"reward":"diplomat","enabled":true},
              {"id":"points_500","name":"理财达人","desc":"累计获得 500 积分","metric":"points_total","target":500,"reward":"saver","enabled":true}
            ]}""";

    private List<TitleDef> parseTitles(String json) {
        List<TitleDef> list = new ArrayList<>();
        try {
            for (JsonNode t : new ObjectMapper().readTree(json).path("titles")) {
                String code = t.path("code").asText("");
                if (code.isBlank()) continue;
                list.add(new TitleDef(code, t.path("name").asText(code), t.path("desc").asText(""),
                        t.path("color").asText("#fbbf24"), t.path("enabled").asBoolean(true)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private List<AchievementDef> parseAchievements(String json) {
        List<AchievementDef> list = new ArrayList<>();
        try {
            for (JsonNode a : new ObjectMapper().readTree(json).path("achievements")) {
                String id = a.path("id").asText("");
                if (id.isBlank()) continue;
                list.add(new AchievementDef(id, a.path("name").asText(id), a.path("desc").asText(""),
                        a.path("metric").asText("playtime_total"), a.path("target").asInt(1),
                        a.path("reward").asText(""), a.path("enabled").asBoolean(true)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public List<TitleDef> titles() {
        String raw = settingService.get(SettingService.KEY_TITLES_CONFIG, String.class);
        List<TitleDef> parsed = raw == null || raw.isBlank() ? parseTitles(DEFAULT_TITLES_JSON) : parseTitles(raw);
        return parsed.isEmpty() ? parseTitles(DEFAULT_TITLES_JSON) : parsed;
    }

    public List<AchievementDef> achievements() {
        String raw = settingService.get(SettingService.KEY_ACHIEVEMENTS_CONFIG, String.class);
        List<AchievementDef> parsed = raw == null || raw.isBlank()
                ? parseAchievements(DEFAULT_ACHIEVEMENTS_JSON) : parseAchievements(raw);
        return parsed.isEmpty() ? parseAchievements(DEFAULT_ACHIEVEMENTS_JSON) : parsed;
    }

    public void saveTitles(String json) {
        settingService.set(SettingService.KEY_TITLES_CONFIG, json == null ? "" : json);
    }

    public void saveAchievements(String json) {
        settingService.set(SettingService.KEY_ACHIEVEMENTS_CONFIG, json == null ? "" : json);
    }

    // ---------- 指标 ----------

    /** 指标值(metric 与 achievements.config 的 metric 键对应) */
    public int metricValue(String username, String metric) {
        return switch (metric == null ? "" : metric) {
            case "playtime_total" -> playtimeTotalSeconds(username);
            case "register_days" -> registerDays(username);
            case "invite_count" -> inviteCount(username);
            case "points_total" -> pointsEarned(username);
            default -> 0;
        };
    }

    /** 累计在线秒数(dp_daily_activity 按账号名与游戏名双键汇总) */
    public int playtimeTotalSeconds(String username) {
        var userOpt = userRepository.findByUsernameIgnoreCase(username);
        String gameName = userOpt.map(u -> u.minecraftName()).orElse(null);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(playtime_seconds),0) FROM dp_daily_activity WHERE username IN (?, ?)",
                Long.class, username, gameName == null ? username : gameName);
        return total == null ? 0 : total.intValue();
    }

    private int registerDays(String username) {
        var r = jdbcTemplate.queryForList(
                "SELECT reg_time FROM dp_user WHERE username = ?", Long.class, username);
        if (r.isEmpty() || r.get(0) == null) return 0;
        return (int) ((System.currentTimeMillis() - r.get(0)) / 86_400_000L);
    }

    private int inviteCount(String username) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dp_user WHERE invited_by = ? AND status = 'approved'",
                Integer.class, username);
        return n == null ? 0 : n;
    }

    private int pointsEarned(String username) {
        var r = jdbcTemplate.queryForList(
                "SELECT COALESCE(SUM(delta),0) FROM dp_points_ledger WHERE username = ? AND delta > 0",
                Long.class, username);
        return r.isEmpty() ? 0 : r.get(0).intValue();
    }

    // ---------- 评估与授予 ----------

    /** 单用户全量评估:进度写 dp_achievement_progress;新达标 → 授予称号+通知 */
    public void evaluate(String username) {
        for (var a : achievements()) {
            if (!a.enabled()) continue;
            int value = metricValue(username, a.metric());
            boolean completed = value >= a.target();
            boolean newlyCompleted = completed && !hasProgressCompleted(username, a.id());
            upsertProgress(username, a.id(), value, completed);
            if (newlyCompleted && !owned(username, a.reward())) {
                grant(username, a.reward());
                notify(username, a.name(), a.reward());
            }
        }
    }

    /** 全量评估(定时任务):所有用户 */
    @Scheduled(fixedDelay = 1_800_000L, initialDelay = 120_000L)
    public void evaluateAll() {
        List<String> usernames = jdbcTemplate.queryForList(
                "SELECT username FROM dp_user", String.class);
        for (String u : usernames) {
            try {
                evaluate(u);
            } catch (Exception e) {
                System.err.println("[称号] 评估失败 " + u + ": " + e.getMessage());
            }
        }
    }

    private void upsertProgress(String username, String achievementId, int progress, boolean completed) {
        jdbcTemplate.update(
                "INSERT INTO dp_achievement_progress (username, achievement_id, progress, completed, completed_at) "
                        + "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE progress = VALUES(progress), completed = VALUES(completed),"
                        + " completed_at = IF(VALUES(completed) = 1 AND completed = 0, VALUES(completed_at), completed_at)",
                username, achievementId, progress, completed ? 1 : 0,
                completed ? System.currentTimeMillis() : null);
    }

    private boolean hasProgressCompleted(String username, String achievementId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dp_achievement_progress WHERE username = ? AND achievement_id = ? AND completed = 1",
                Integer.class, username, achievementId);
        return n != null && n > 0;
    }

    private boolean owned(String username, String code) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dp_user_titles WHERE username = ? AND title_code = ?",
                Integer.class, username, code);
        return n != null && n > 0;
    }

    public void grant(String username, String code) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO dp_user_titles (username, title_code, obtained_at) VALUES (?,?,?)",
                username, code, System.currentTimeMillis());
    }

    private void notify(String username, String achievementName, String titleCode) {
        String titleName = titleName(titleCode);
        notificationRepository.save(new NotificationRecord(null, username, "achievement",
                "获得新称号", "达成成就「" + achievementName + "」,获得称号「" + titleName + "」",
                null, null, username));
    }

    private String titleName(String code) {
        for (var t : titles()) if (t.code().equals(code)) return t.name();
        return code;
    }

    // ---------- 佩戴 ----------

    public void equip(String username, String code) {
        if (!owned(username, code)) {
            throw new IllegalStateException("未拥有该称号");
        }
        jdbcTemplate.update(
                "INSERT INTO dp_title_equipped (username, title_code) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE title_code = VALUES(title_code)",
                username, code);
    }

    public void unequip(String username) {
        jdbcTemplate.update("DELETE FROM dp_title_equipped WHERE username = ?", username);
    }

    /** 当前佩戴称号(code),未佩戴返回 null */
    public String activeTitleCode(String username) {
        var r = jdbcTemplate.queryForList(
                "SELECT title_code FROM dp_title_equipped WHERE username = ?", String.class, username);
        return r.isEmpty() ? null : r.get(0);
    }

    /** 佩戴中的称号定义(未佩戴/定义缺失返回 null) */
    public TitleDef activeTitle(String username) {
        String code = activeTitleCode(username);
        if (code == null) return null;
        for (var t : titles()) if (t.code().equals(code)) return t;
        return null;
    }

    // ---------- 管理端 ----------

    /** 拥有记录行(title_code/obtained_at) */
    public List<Map<String, Object>> ownedRows(String username) {
        return jdbcTemplate.queryForList(
                "SELECT title_code, obtained_at FROM dp_user_titles WHERE username = ? ORDER BY obtained_at",
                username);
    }

    /** 称号持有者数量 */
    public int holders(String code) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dp_user_titles WHERE title_code = ?", Integer.class, code);
        return n == null ? 0 : n;
    }

    public void saveTitlesFromMaps(List<Map<String, Object>> titles) {
        settingService.set(SettingService.KEY_TITLES_CONFIG, Map.of("titles", titles));
    }

    public void saveAchievementsFromMaps(List<Map<String, Object>> achievements) {
        settingService.set(SettingService.KEY_ACHIEVEMENTS_CONFIG, Map.of("achievements", achievements));
    }

    public boolean userExists(String username) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dp_user WHERE username = ?", Integer.class, username);
        return n != null && n > 0;
    }

    /** 撤销称号(同时清佩戴) */
    public void revoke(String username, String code) {
        jdbcTemplate.update("DELETE FROM dp_user_titles WHERE username = ? AND title_code = ?", username, code);
        jdbcTemplate.update("DELETE FROM dp_title_equipped WHERE username = ? AND title_code = ?", username, code);
    }
}
