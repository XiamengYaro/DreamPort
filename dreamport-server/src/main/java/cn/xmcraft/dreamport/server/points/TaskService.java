package cn.xmcraft.dreamport.server.points;

import cn.xmcraft.dreamport.server.settings.SettingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 任务规则引擎:任务定义存 dp_setting tasks.config(JSON,后台可视化 CRUD 维护),
 * 进度按四类计算(signin 签到触发 / playtime 日结驱动 / streak 连续日期推算 / full_attendance 满勤)。
 * 完成后进"可领取"态,玩家在任务中心手动领取积分(提升回访)。
 */
@Service
public class TaskService {

    private final SettingService settingService;
    private final JdbcTemplate jdbcTemplate;
    private final PointsService pointsService;

    public TaskService(SettingService settingService, JdbcTemplate jdbcTemplate, PointsService pointsService) {
        this.settingService = settingService;
        this.jdbcTemplate = jdbcTemplate;
        this.pointsService = pointsService;
    }

    public record TaskDef(String id, String type, String channel, String period,
                          String name, String desc, int target, int points, boolean enabled) {}

    public record TaskView(String id, String type, String period, String name, String desc,
                           int points, int target, int progress, boolean completed, boolean claimed) {}

    /** 内置默认任务清单(后台未配置/清空时使用) */
    public static final String DEFAULT_TASKS_JSON = """
            {"tasks":[
              {"id":"signin_web","type":"signin","channel":"web","period":"daily","name":"网页签到","desc":"每日登录官网签到","target":1,"points":5,"enabled":true},
              {"id":"signin_game","type":"signin","channel":"game","period":"daily","name":"游戏内签到","desc":"进服后输入 /xmw signin 签到","target":1,"points":10,"enabled":true},
              {"id":"playtime_daily_10","type":"playtime","period":"daily","name":"今日在线 10 分钟","desc":"当日游戏内在线满 10 分钟","target":600,"points":5,"enabled":true},
              {"id":"playtime_daily_30","type":"playtime","period":"daily","name":"今日在线 30 分钟","desc":"当日游戏内在线满 30 分钟","target":1800,"points":10,"enabled":true},
              {"id":"playtime_daily_60","type":"playtime","period":"daily","name":"今日在线 60 分钟","desc":"当日游戏内在线满 60 分钟","target":3600,"points":15,"enabled":true},
              {"id":"playtime_daily_120","type":"playtime","period":"daily","name":"今日在线 120 分钟","desc":"当日游戏内在线满 120 分钟","target":7200,"points":30,"enabled":true},
              {"id":"playtime_week_5h","type":"playtime","period":"weekly","name":"本周在线 5 小时","desc":"本周游戏内累计在线 5 小时","target":18000,"points":50,"enabled":true},
              {"id":"streak_month_7","type":"streak","period":"monthly","name":"连续登录 7 天","desc":"当月连续登录游戏 7 天","target":7,"points":30,"enabled":true},
              {"id":"streak_month_14","type":"streak","period":"monthly","name":"连续登录 14 天","desc":"当月连续登录游戏 14 天","target":14,"points":60,"enabled":true},
              {"id":"streak_month_21","type":"streak","period":"monthly","name":"连续登录 21 天","desc":"当月连续登录游戏 21 天","target":21,"points":100,"enabled":true},
              {"id":"streak_month_28","type":"streak","period":"monthly","name":"连续登录 28 天","desc":"当月连续登录游戏 28 天","target":28,"points":150,"enabled":true},
              {"id":"full_attendance","type":"full_attendance","period":"monthly","name":"当月满勤","desc":"当月每日均登录游戏","target":0,"points":200,"enabled":true}
            ]}""";

    public List<TaskDef> tasks() {
        String raw = settingService.get(SettingService.KEY_TASKS_CONFIG, String.class);
        if (raw == null || raw.isBlank()) {
            return parse(DEFAULT_TASKS_JSON);
        }
        List<TaskDef> parsed = parse(raw);
        return parsed.isEmpty() ? parse(DEFAULT_TASKS_JSON) : parsed;
    }

    private List<TaskDef> parse(String json) {
        List<TaskDef> list = new ArrayList<>();
        try {
            JsonNode node = new ObjectMapper().readTree(json).path("tasks");
            for (JsonNode t : node) {
                String id = t.path("id").asText("");
                if (id.isBlank()) continue;
                list.add(new TaskDef(id, t.path("type").asText("signin"), t.path("channel").asText(""),
                        t.path("period").asText("daily"), t.path("name").asText(id), t.path("desc").asText(""),
                        t.path("target").asInt(1), t.path("points").asInt(0), t.path("enabled").asBoolean(true)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /** period → 周期键 */
    public String periodKey(String period) {
        LocalDate today = LocalDate.now();
        return switch (period == null ? "daily" : period) {
            case "weekly" -> today.get(WeekFields.ISO.weekBasedYear()) + "-W"
                    + String.format("%02d", today.get(WeekFields.ISO.weekOfWeekBasedYear()));
            case "monthly" -> today.toString().substring(0, 7);
            default -> today.toString();
        };
    }

    /** 触发:签到(玩家/游戏双端) */
    public void onSignin(String username, String channel) {
        evaluate(username, "signin", channel);
        evaluate(username, "streak", null);
        evaluate(username, "full_attendance", null);
    }

    /** 触发:会话时长上报 */
    public void onActivity(String username) {
        evaluate(username, "playtime", null);
        evaluate(username, "full_attendance", null);
    }

    /**
     * 会话时长落库(修复审计 H4):按账号+日期 upsert dp_daily_activity,
     * 这是 playtime 任务/在线成就/满勤连击的数据源,此前全库无写入导致功能永不推进。
     */
    public void recordActivity(String username, long sessionSeconds, int loginCount) {
        if (username == null || username.isBlank()) {
            return;
        }
        String date = LocalDate.now().toString();
        jdbcTemplate.update(
                "INSERT INTO dp_daily_activity (username, activity_date, playtime_seconds, login_count) "
                        + "VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE playtime_seconds = playtime_seconds + VALUES(playtime_seconds), "
                        + "login_count = login_count + VALUES(login_count)",
                username, date, Math.max(0, sessionSeconds), Math.max(0, loginCount));
    }

    /** 全量评估(center 拉取兜底) */
    public void evaluateAll(String username) {
        evaluate(username, "signin", null);
        evaluate(username, "playtime", null);
        evaluate(username, "streak", null);
        evaluate(username, "full_attendance", null);
    }

    /** 评估某类型下全部启用任务,写 dp_task_progress */
    private void evaluate(String username, String type, String channel) {
        LocalDate today = LocalDate.now();
        for (TaskDef t : tasks()) {
            if (!t.enabled() || !t.type().equals(type)) continue;
            String pk = periodKey(t.period());
            int progress = computeProgress(username, t, pk, today);
            jdbcTemplate.update(
                    "INSERT INTO dp_task_progress (username, task_id, period_key, progress, completed) VALUES (?,?,?,?,?) "
                            + "ON DUPLICATE KEY UPDATE progress = VALUES(progress), completed = VALUES(completed)",
                    username, t.id(), pk, progress, progress >= t.target() ? 1 : 0);
        }
    }

    private int computeProgress(String username, TaskDef t, String pk, LocalDate today) {
        return switch (t.type()) {
            case "signin" -> countSignin(username, today.toString(), t.channel());
            case "playtime" -> "daily".equals(t.period())
                    ? playtimeOn(username, today.toString())
                    : playtimeBetween(username, periodStart(t.period()), today.toString());
            case "streak" -> maxStreak(username, today.withDayOfMonth(1).toString(), today.toString());
            case "full_attendance" -> activeDays(username, today.withDayOfMonth(1).toString(), today.toString());
            default -> 0;
        };
    }

    private String periodStart(String period) {
        LocalDate today = LocalDate.now();
        if ("weekly".equals(period)) {
            return today.minusDays((today.getDayOfWeek().getValue() + 6) % 7L).toString(); // 本周一
        }
        if ("monthly".equals(period)) {
            return today.withDayOfMonth(1).toString();
        }
        return today.toString();
    }

    private int countSignin(String username, String date, String channel) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dp_signin WHERE username = ? AND sign_date = ? AND source = ?",
                Integer.class, username, date, channel);
        return n == null ? 0 : n;
    }

    private int playtimeOn(String username, String date) {
        var r = jdbcTemplate.queryForList(
                "SELECT playtime_seconds FROM dp_daily_activity WHERE username = ? AND activity_date = ?",
                Long.class, username, date);
        return r.isEmpty() ? 0 : r.get(0).intValue();
    }

    private int playtimeBetween(String username, String start, String end) {
        var r = jdbcTemplate.queryForList(
                "SELECT COALESCE(SUM(playtime_seconds),0) FROM dp_daily_activity WHERE username = ? AND activity_date BETWEEN ? AND ?",
                Long.class, username, start, end);
        return r.isEmpty() ? 0 : r.get(0).intValue();
    }

    private List<LocalDate> activeDates(String username, String start, String end) {
        List<LocalDate> dates = new ArrayList<>();
        for (var row : jdbcTemplate.queryForList(
                "SELECT activity_date FROM dp_daily_activity WHERE username = ? AND activity_date BETWEEN ? AND ?",
                String.class, username, start, end)) {
            try {
                dates.add(LocalDate.parse(row));
            } catch (Exception ignored) {
            }
        }
        for (var row : jdbcTemplate.queryForList(
                "SELECT DISTINCT sign_date FROM dp_signin WHERE username = ? AND sign_date BETWEEN ? AND ?",
                String.class, username, start, end)) {
            try {
                dates.add(LocalDate.parse(row));
            } catch (Exception ignored) {
            }
        }
        return dates.stream().distinct().sorted().toList();
    }

    /** 当月最大连续活跃天数 */
    private int maxStreak(String username, String start, String end) {
        List<LocalDate> dates = activeDates(username, start, end);
        int best = 0, run = 0;
        LocalDate prev = null;
        for (LocalDate d : dates) {
            run = (prev != null && d.equals(prev.plusDays(1))) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }
        return best;
    }

    private int activeDays(String username, String start, String end) {
        return activeDates(username, start, end).size();
    }

    /** 任务中心:全量评估后按周期分组 */
    public Map<String, Object> center(String username) {
        evaluateAll(username);
        int balance = jdbcTemplate.queryForList(
                "SELECT balance FROM dp_points_balance WHERE username = ?", Integer.class, username)
                .stream().findFirst().orElse(0);

        Map<String, Boolean> signedToday = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (String src : List.of("web", "game")) {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM dp_signin WHERE username = ? AND sign_date = ? AND source = ?",
                    Integer.class, username, today.toString(), src);
            signedToday.put(src, n != null && n > 0);
        }

        Map<String, List<TaskView>> byPeriod = new LinkedHashMap<>();
        byPeriod.put("daily", new ArrayList<>());
        byPeriod.put("weekly", new ArrayList<>());
        byPeriod.put("monthly", new ArrayList<>());
        String dailyPk = periodKey("daily");
        String weeklyPk = periodKey("weekly");
        String monthlyPk = periodKey("monthly");
        for (TaskDef t : tasks()) {
            if (!t.enabled()) continue;
            String pk = switch (t.period()) {
                case "weekly" -> weeklyPk;
                case "monthly" -> monthlyPk;
                default -> dailyPk;
            };
            var rows = jdbcTemplate.queryForList(
                    "SELECT progress, claimed FROM dp_task_progress WHERE username = ? AND task_id = ? AND period_key = ?",
                    username, t.id(), pk);
            int progress = rows.isEmpty() ? 0 : ((Number) rows.get(0).get("progress")).intValue();
            boolean claimed = !rows.isEmpty() && ((Number) rows.get(0).get("claimed")).intValue() > 0;
            boolean completed = progress >= t.target();
            byPeriod.getOrDefault(t.period(), byPeriod.get("daily")).add(
                    new TaskView(t.id(), t.type(), t.period(), t.name(), t.desc(), t.points(), targetOf(t, today),
                            progress, completed, claimed));
        }

        int streak = maxStreak(username, today.withDayOfMonth(1).toString(), today.toString());
        Map<String, Object> signin = new LinkedHashMap<>();
        signin.put("web", signedToday.get("web"));
        signin.put("game", signedToday.get("game"));
        signin.put("streak", streak);
        signin.put("monthDays", activeDays(username, today.withDayOfMonth(1).toString(), today.toString()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", balance);
        data.put("signin", signin);
        data.put("tasks", byPeriod);
        return data;
    }

    /** 满勤任务 target 动态=当月天数;其余用配置值 */
    private int targetOf(TaskDef t, LocalDate today) {
        return "full_attendance".equals(t.type()) ? today.lengthOfMonth() : t.target();
    }

    /** 领取已完成任务奖励 */
    public synchronized Map<String, Object> claim(String username, String taskId) {
        TaskDef def = tasks().stream().filter(t -> t.id().equals(taskId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        String pk = periodKey(def.period());
        int updated = jdbcTemplate.update(
                "UPDATE dp_task_progress SET claimed = 1, claimed_at = ? "
                        + "WHERE username = ? AND task_id = ? AND period_key = ? AND completed = 1 AND claimed = 0",
                System.currentTimeMillis(), username, taskId, pk);
        if (updated == 0) {
            throw new IllegalStateException("任务未完成或奖励已领取");
        }
        int bal = pointsService.earn(username, def.points(), "task", taskId, def.name());
        return Map.of("points", def.points(), "name", def.name(), "balance", bal);
    }

    /** 签到(每日每用户每渠道一次);返回是否为本日首次 */
    public boolean signin(String username, String channel) {
        String date = LocalDate.now().toString();
        try {
            jdbcTemplate.update("INSERT INTO dp_signin (username, sign_date, source, created_at) VALUES (?,?,?,?)",
                    username, date, channel, System.currentTimeMillis());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return false;
        }
        return true;
    }

    // ---------- 兑换商店 ----------

    public record Reward(String id, String name, String desc, int cost, boolean enabled) {}

    private static final String DEFAULT_SHOP_JSON = """
            {"rewards":[
              {"id":"coin_1000","name":"1000 硬币","desc":"游戏内邮箱领取后自动发放","cost":500,"commands":[{"type":"command","cmd":"eco give {player} 1000"}],"enabled":true},
              {"id":"netherite_x8","name":"下界合金锭 ×8","desc":"游戏内邮箱领取后自动发放","cost":300,"commands":[{"type":"command","cmd":"give {player} minecraft:netherite_ingot 8"}],"enabled":true},
              {"id":"enchbook_pack","name":"附魔书礼包","desc":"游戏内邮箱领取后自动发放","cost":200,"commands":[{"type":"command","cmd":"give {player} minecraft:enchanted_book 3"}],"enabled":true}
            ]}""";

    public List<Reward> shopRewards() {
        String raw = settingService.get(SettingService.KEY_SHOP_CONFIG, String.class);
        List<Reward> parsed = raw == null || raw.isBlank() ? parseShop(DEFAULT_SHOP_JSON) : parseShop(raw);
        return parsed.isEmpty() ? parseShop(DEFAULT_SHOP_JSON) : parsed;
    }

    private List<Reward> parseShop(String json) {
        List<Reward> list = new ArrayList<>();
        try {
            for (JsonNode r : new ObjectMapper().readTree(json).path("rewards")) {
                String id = r.path("id").asText("");
                if (id.isBlank()) continue;
                list.add(new Reward(id, r.path("name").asText(id), r.path("desc").asText(""),
                        r.path("cost").asInt(0), r.path("enabled").asBoolean(true)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /** 兑换:扣积分 → 奖励邮件入队(commands 中 {player} 已替换为实际用户名) */
    public Map<String, Object> redeem(String username, String rewardId, MailService mail) {
        Reward reward = shopRewards().stream().filter(r -> r.id().equals(rewardId) && r.enabled()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("兑换项不存在或已下架"));
        int bal = pointsService.spend(username, reward.cost(), "redeem", reward.id(), reward.name());
        String commands = shopRawCommands(rewardId).replace("{player}", username);
        mail.enqueue(username, "积分兑换:" + reward.name(), commands, "兑换消耗 " + reward.cost() + " 积分");
        return Map.of("name", reward.name(), "balance", bal);
    }

    private String shopRawCommands(String rewardId) {
        String raw = settingService.get(SettingService.KEY_SHOP_CONFIG, String.class);
        String src = raw == null || raw.isBlank() ? DEFAULT_SHOP_JSON : raw;
        try {
            for (JsonNode r : new ObjectMapper().readTree(src).path("rewards")) {
                if (rewardId.equals(r.path("id").asText())) {
                    return r.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return "{}";
    }
}
