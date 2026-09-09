package cn.xmcraft.dreamport.server.reward;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.notification.NotificationRecord;
import cn.xmcraft.dreamport.server.notification.NotificationRepository;
import cn.xmcraft.dreamport.server.points.MailService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 奖励礼包域：
 * Web 创建模板(capturing) → 服内管理员 /xmw kit save &lt;名&gt; 采集背包上传(ready,物品经
 * Paper ItemStack#serializeAsBytes 序列化为 Base64) → 后台发放时把 items/commands **快照**
 * 进 dp_mail(自包含,发放后改模板不影响已发邮件) → 玩家 /mail 领取(物品直发+指令执行)。
 * 物品 JSON:[{"s":"&lt;base64&gt;","n":"展示名","c":数量}];附加指令沿用商店格式
 * [{"type":"command","cmd":"..."}],{player} 占位符由插件领取时替换。
 */
@Service
public class RewardKitService {

    private static final Logger log = LoggerFactory.getLogger(RewardKitService.class);
    /** 单礼包物品 JSON 上限(36 格背包含 NBT 一般 <200KB,1MB 为安全余量) */
    private static final int MAX_ITEMS_JSON_BYTES = 1024 * 1024;
    /** 全员批量发放人数上限(防误操作撑爆 dp_mail) */
    private static final int MAX_RECIPIENTS = 2000;

    private final JdbcTemplate jdbc;
    private final MailService mailService;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final AuditService auditService;
    private final ObjectMapper mapper = new ObjectMapper();

    public RewardKitService(JdbcTemplate jdbc, MailService mailService, UserRepository userRepository,
                            NotificationRepository notificationRepository, AuditService auditService) {
        this.jdbc = jdbc;
        this.mailService = mailService;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.auditService = auditService;
    }

    public record Result(boolean success, String message) {
    }

    public record KitRow(long id, String name, String note, String summary, String status,
                         String capturedBy, Long capturedAt, String createdBy, Long createdAt,
                         String commands) {
    }

    public record SendOutcome(int sent, int skipped, String message) {
    }

    // ---------- 模板管理 ----------

    public synchronized Result create(String name, String note, String operator) {
        if (name == null || !name.matches("^[A-Za-z0-9_\\-\\u4e00-\\u9fff]{2,32}$")) {
            return new Result(false, "礼包名不合法（2-32 位中文/字母/数字/_/-）");
        }
        Integer dup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dp_reward_kit WHERE name = ?", Integer.class, name);
        if (dup != null && dup > 0) {
            return new Result(false, "同名礼包已存在");
        }
        jdbc.update("INSERT INTO dp_reward_kit (name, note, status, created_by, created_at) VALUES (?,?, 'capturing', ?, ?)",
                name, note == null || note.isBlank() ? null : note, operator, System.currentTimeMillis());
        auditService.log("kit_create", operator, name, null);
        return new Result(true, "礼包「" + name + "」已创建，请在游戏内执行 /xmw kit save " + name + " 采集背包内容");
    }

    /** 服内采集上传：校验模板存在且未停用、大小与 JSON 格式，更新为 ready */
    public synchronized Result saveCapture(String name, String player, String itemsJson, String summary) {
        var rows = jdbc.queryForList(
                "SELECT id, status FROM dp_reward_kit WHERE name = ?", name);
        if (rows.isEmpty()) {
            return new Result(false, "礼包「" + name + "」不存在，请先在管理后台创建");
        }
        String status = String.valueOf(rows.get(0).get("status"));
        if ("disabled".equals(status)) {
            return new Result(false, "礼包「" + name + "」已停用，无法采集");
        }
        if (itemsJson == null || itemsJson.isBlank()) {
            return new Result(false, "采集内容为空");
        }
        if (itemsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_ITEMS_JSON_BYTES) {
            return new Result(false, "礼包内容超出大小上限（1MB）");
        }
        try {
            JsonNode node = mapper.readTree(itemsJson);
            if (!node.isArray() || node.isEmpty()) {
                return new Result(false, "物品数据格式错误（需非空数组）");
            }
            for (JsonNode item : node) {
                if (item.path("s").asText("").isBlank()) {
                    return new Result(false, "物品数据格式错误（缺少序列化内容）");
                }
            }
        } catch (Exception e) {
            return new Result(false, "物品数据不是合法 JSON");
        }
        jdbc.update("UPDATE dp_reward_kit SET items = ?, summary = ?, status = 'ready', "
                        + "captured_by = ?, captured_at = ?, updated_at = ? WHERE name = ?",
                itemsJson, truncate(summary, 255), player, System.currentTimeMillis(),
                System.currentTimeMillis(), name);
        auditService.log("kit_capture", player, name, truncate(summary, 200));
        log.info("[礼包] {} 采集了礼包「{}」（{}）", player, name, summary);
        return new Result(true, "礼包「" + name + "」内容已保存（" + truncate(summary, 80) + "）");
    }

    public List<KitRow> list() {
        List<KitRow> kits = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT id, name, note, summary, status, captured_by, captured_at, created_by, created_at, commands "
                        + "FROM dp_reward_kit ORDER BY created_at DESC")) {
            kits.add(new KitRow(
                    ((Number) row.get("id")).longValue(),
                    String.valueOf(row.get("name")),
                    row.get("note") == null ? "" : String.valueOf(row.get("note")),
                    row.get("summary") == null ? "" : String.valueOf(row.get("summary")),
                    String.valueOf(row.get("status")),
                    row.get("captured_by") == null ? "" : String.valueOf(row.get("captured_by")),
                    row.get("captured_at") == null ? null : ((Number) row.get("captured_at")).longValue(),
                    String.valueOf(row.get("created_by")),
                    ((Number) row.get("created_at")).longValue(),
                    row.get("commands") == null ? "" : String.valueOf(row.get("commands"))));
        }
        return kits;
    }

    public synchronized Result delete(long id, String operator) {
        int updated = jdbc.update("DELETE FROM dp_reward_kit WHERE id = ?", id);
        if (updated == 0) {
            return new Result(false, "礼包不存在");
        }
        auditService.log("kit_delete", operator, "#" + id, null);
        return new Result(true, "礼包已删除");
    }

    /** 编辑礼包:备注 + 附加指令(每行一条,支持 {player} 占位符;指令与采集物品可混合) */
    public synchronized Result update(long id, String note, String commandsText, String operator) {
        var rows = jdbc.queryForList("SELECT name FROM dp_reward_kit WHERE id = ?", id);
        if (rows.isEmpty()) {
            return new Result(false, "礼包不存在");
        }
        String commandsJson = "[]";
        if (commandsText != null && !commandsText.isBlank()) {
            List<String> cmds = commandsText.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
            StringBuilder sb = new StringBuilder("{\"commands\":[");
            for (int i = 0; i < cmds.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append("{\"type\":\"command\",\"cmd\":\"")
                        .append(cmds.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
                        .append("\"}");
            }
            commandsJson = sb.append("]}").toString();
        }
        jdbc.update("UPDATE dp_reward_kit SET note = ?, commands = ?, updated_at = ? WHERE id = ?",
                note == null || note.isBlank() ? null : note, commandsJson, System.currentTimeMillis(), id);
        auditService.log("kit_update", operator, String.valueOf(rows.get(0).get("name")), null);
        return new Result(true, "礼包已更新");
    }

    // ---------- 发放 ----------

    /**
     * 发放奖励（快照进 dp_mail）。
     *
     * @param usernames    指定玩家（all=true 时忽略）
     * @param all          全员（全部 approved 玩家）
     * @param kitId        礼包模式：模板 id（items+commands 快照）
     * @param commandsText 指令包模式：每行一条指令（支持 {player} 占位符）
     * @param title        邮件标题（可空取默认）
     */
    public synchronized SendOutcome send(List<String> usernames, boolean all, Long kitId,
                                         String commandsText, String title, String note, String operator) {
        String itemsJson = null;
        String commandsJson = null;
        String mailTitle;
        String mailNote = note == null || note.isBlank() ? "来自管理员的奖励，进服输入 /mail 领取" : note;

        if (kitId != null) {
            var kits = jdbc.queryForList(
                    "SELECT name, items, commands, status FROM dp_reward_kit WHERE id = ?", kitId);
            if (kits.isEmpty()) {
                return new SendOutcome(0, 0, "礼包不存在");
            }
            Map<String, Object> kit = kits.get(0);
            if (!"ready".equals(kit.get("status")) || kit.get("items") == null) {
                return new SendOutcome(0, 0, "礼包尚未完成采集（需服内 /xmw kit save 上传内容）");
            }
            itemsJson = String.valueOf(kit.get("items"));
            commandsJson = kit.get("commands") == null ? "[]" : String.valueOf(kit.get("commands"));
            mailTitle = title == null || title.isBlank() ? "礼包奖励：" + kit.get("name") : title;
        } else {
            List<String> cmds = commandsText == null ? List.of() : commandsText.lines()
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (cmds.isEmpty()) {
                return new SendOutcome(0, 0, "请选择礼包或填写至少一条奖励指令");
            }
            // 与商店一致的指令包格式(对象包裹 commands 数组;插件侧同时兼容裸数组)
            StringBuilder sb = new StringBuilder("{\"commands\":[");
            for (int i = 0; i < cmds.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append("{\"type\":\"command\",\"cmd\":\"")
                        .append(cmds.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
                        .append("\"}");
            }
            commandsJson = sb.append("]}").toString();
            itemsJson = null;
            mailTitle = title == null || title.isBlank() ? "管理员奖励" : title;
        }

        // 收件人
        List<String> recipients = new ArrayList<>();
        int skipped = 0;
        if (all) {
            for (var u : userRepository.listAll()) {
                if ("approved".equals(u.status())) {
                    recipients.add(u.username());
                }
            }
        } else {
            for (String name : usernames == null ? List.<String>of() : usernames) {
                var found = userRepository.findByUsernameIgnoreCase(name);
                if (found.isPresent() && !"banned".equals(found.get().status())) {
                    recipients.add(found.get().username());
                } else {
                    skipped++;
                }
            }
        }
        if (recipients.isEmpty()) {
            return new SendOutcome(0, skipped, "没有可发放的玩家");
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            return new SendOutcome(0, 0, "发放人数超过上限（" + MAX_RECIPIENTS + "）");
        }

        for (String r : recipients) {
            mailService.enqueue(r, mailTitle, commandsJson, mailNote, itemsJson);
            notificationRepository.save(new NotificationRecord(
                    null, r, "reward_mail", "收到奖励邮件",
                    "管理员向你发放了「" + mailTitle + "」，进服输入 /mail 领取", null, null, null));
        }
        String target = all ? "all(" + recipients.size() + ")" : String.join(",", recipients);
        auditService.log("reward_send", operator, target,
                mailTitle + " x" + recipients.size() + (itemsJson != null ? "(含物品)" : "(指令)"));
        log.info("[发放] {} 发放「{}」给 {} 名玩家（{}）", operator, mailTitle, recipients.size(),
                itemsJson != null ? "含物品" : "指令");
        return new SendOutcome(recipients.size(), skipped, "已发放「" + mailTitle + "」给 " + recipients.size() + " 名玩家"
                + (skipped > 0 ? "，跳过 " + skipped + " 个无效玩家" : ""));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
