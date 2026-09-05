package cn.xmcraft.dreamport.server.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧库自动迁移器（P2，Rules.md §4 迁移器契约）：
 * 1) 检测旧表（xmwhitelist_* / village_trades / public_machines）
 * 2) 仅当 dp_user 为空时执行：ALTER TABLE … RENAME TO legacy_<表>_backup（原地备份，零拷贝）
 * 3) 逐行导入 dp_* 表（密码哈希原样保留 → password_algo='legacy_sha256'）
 * 4) users.json / audits.json（file 模式）导入
 * 5) 旧 config.yml 的站点内容 → dp_setting
 * 幂等：dp_user 非空或旧表不存在时自动跳过；报告写入日志与 dp_setting(migration.report)。
 */
public class LegacyMigrator {

    private static final Logger log = LoggerFactory.getLogger(LegacyMigrator.class);

    /** 旧表 → 新表 与列名映射（与 ../XMWhitelist-Legacy 各 Mysql*Dao 的 DDL 一一对应） */
    private static final Map<String, String> TABLE_MAP = Map.ofEntries(
            Map.entry("xmwhitelist_users", "dp_user"),
            Map.entry("xmwhitelist_audits", "dp_audit_log"),
            Map.entry("xmwhitelist_invites", "dp_invite"),
            Map.entry("xmwhitelist_notifications", "dp_notification"),
            Map.entry("xmwhitelist_pending_logins", "dp_pending_login"),
            Map.entry("xmwhitelist_password_resets", "dp_password_reset"),
            Map.entry("xmwhitelist_appeals", "dp_appeal"),
            Map.entry("village_trades", "dp_village_trade"),
            Map.entry("public_machines", "dp_public_machine"));

    /** 旧列名 → 新列名（未列出的同名复制）；is_read→is_read 等同名自动处理 */
    private static final Map<String, String> COLUMN_RENAME = Map.ofEntries(
            Map.entry("timestamp", "created_at"),
            Map.entry("is_read", "is_read"),
            Map.entry("message", "message"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public LegacyMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> migrateAll(String legacyDir, String legacyConfigPath) {
        Map<String, Object> report = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        if (!dpUserEmpty()) {
            report.put("skipped", "dp_user 已有数据，跳过迁移（幂等保护）");
            log.info("[迁移] {}", report.get("skipped"));
            return report;
        }
        List<String> renamed = new ArrayList<>();
        Map<String, Integer> imported = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : TABLE_MAP.entrySet()) {
            if (tableExists(e.getKey())) {
                String backup = "legacy_" + e.getKey() + "_backup";
                jdbc.execute("ALTER TABLE " + e.getKey() + " RENAME TO " + backup);
                renamed.add(e.getKey());
                int rows = importTable(backup, e.getValue());
                imported.put(e.getKey(), rows);
            }
        }
        report.put("renamedTables", renamed);
        report.put("importedRows", imported);
        try {
            JsonImportResult json = importJsonFiles(legacyDir);
            report.put("jsonImport", json.report());
        } catch (Exception e) {
            report.put("jsonImportError", String.valueOf(e.getMessage()));
        }
        try {
            report.put("configImport", new LegacyConfigImporter(jdbc).importConfig(legacyConfigPath));
        } catch (Exception e) {
            report.put("configImportError", String.valueOf(e.getMessage()));
        }
        report.put("elapsedMs", System.currentTimeMillis() - start);
        log.info("[迁移] 完成：重命名 {} 张表，导入 {} 行，耗时 {}ms",
                renamed.size(), imported.values().stream().mapToInt(Integer::intValue).sum(),
                System.currentTimeMillis() - start);
        try {
            jdbc.update("INSERT INTO dp_setting (skey, svalue, updated_at, updated_by) VALUES (?, ?, ?, ?)",
                    "migration.report", mapper.writeValueAsString(report),
                    System.currentTimeMillis(), "migrator");
        } catch (Exception ignored) {
            // dp_setting 不存在（极旧库）时忽略报告落库
        }
        return report;
    }

    /** 管理后台上传迁移：从暂存表复制到 dp_* 表（不_rename，导入后由调用方清理暂存） */
    public Map<String, Object> migrateFromStaging(Map<String, String> stagingToTarget) {
        Map<String, Object> report = new LinkedHashMap<>();
        if (!dpUserEmpty()) {
            report.put("skipped", "dp_user 已有数据，禁止覆盖导入（幂等保护）");
            log.info("[迁移] {}", report.get("skipped"));
            return report;
        }
        long start = System.currentTimeMillis();
        Map<String, Integer> imported = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : stagingToTarget.entrySet()) {
            jdbc.update("DELETE FROM " + e.getValue());
            imported.put(e.getKey(), importTable(e.getKey(), e.getValue()));
        }
        report.put("importedRows", imported);
        report.put("elapsedMs", System.currentTimeMillis() - start);
        log.info("[迁移] 上传数据导入完成：{} 行，耗时 {}ms",
                imported.values().stream().mapToInt(Integer::intValue).sum(),
                System.currentTimeMillis() - start);
        try {
            jdbc.update("DELETE FROM dp_setting WHERE skey = ?", "migration.report");
            jdbc.update("INSERT INTO dp_setting (skey, svalue, updated_at, updated_by) VALUES (?, ?, ?, ?)",
                    "migration.report", mapper.writeValueAsString(report),
                    System.currentTimeMillis(), "admin-upload");
        } catch (Exception ignored) {
        }
        return report;
    }

    public boolean dpUserHasData() {
        return !dpUserEmpty();
    }

    /** 单表导入：按 ResultSetMetaData 动态取列，同名/改名映射；返回导入行数 */
    int importTable(String legacyTable, String newTable) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + legacyTable);
        for (Map<String, Object> row : rows) {
            Map<String, Object> target = new LinkedHashMap<>();
            for (Map.Entry<String, Object> col : row.entrySet()) {
                String newCol = COLUMN_RENAME.getOrDefault(col.getKey().toLowerCase(), col.getKey().toLowerCase());
                target.put(newCol, col.getValue());
            }
            if ("dp_user".equals(newTable)) {
                Object hash = target.get("password");
                if (hash != null && String.valueOf(hash).startsWith("$SHA$")) {
                    target.put("password_algo", "legacy_sha256");
                    target.put("password_hash", hash);
                }
                target.remove("password");
                target.remove("id"); // username 为业务键，主键交给自增
            } else {
                target.remove("id");
            }
            List<String> cols = new ArrayList<>(target.keySet());
            String colSql = String.join(", ", cols);
            String placeholders = String.join(", ", cols.stream().map(c -> "?").toList());
            jdbc.update("INSERT INTO " + newTable + " (" + colSql + ") VALUES (" + placeholders + ")",
                    cols.stream().map(target::get).toArray());
        }
        return rows.size();
    }

    public record JsonImportResult(int users, int audits, List<String> report) {
    }

    /** users.json（Map<用户名, UserData>）与 audits.json（数组）导入 */
    @SuppressWarnings("unchecked")
    public JsonImportResult importJsonFiles(String legacyDir) throws Exception {
        List<String> report = new ArrayList<>();
        int users = 0;
        int audits = 0;
        var mapper2 = mapper;
        if (legacyDir != null && !legacyDir.isBlank()) {
            var userPath = java.nio.file.Path.of(legacyDir, "users.json");
            var auditPath = java.nio.file.Path.of(legacyDir, "audits.json");
            if (java.nio.file.Files.exists(userPath)) {
                Map<String, Object> data = mapper2.readValue(java.nio.file.Files.readString(userPath),
                        Map.class);
                for (Map.Entry<String, Object> e : data.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> u = (Map<String, Object>) e.getValue();
                    if (u == null || userRepositoryExists(u)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("username", firstNonNull(u.get("username"), e.getKey()));
                    row.put("email", u.get("email"));
                    row.put("status", orDefault(u.get("status"), "pending"));
                    row.put("password_algo", "legacy_sha256");
                    row.put("password_hash", orDefault(u.get("password"), "$SHA$$"));
                    row.put("reg_time", orDefault(u.get("regTime"), System.currentTimeMillis()));
                    row.put("discord_id", u.get("discordId"));
                    row.put("qq_number", u.get("qqNumber"));
                    row.put("qq_bound_at", u.get("qqBoundAt"));
                    row.put("questionnaire_score", orDefault(u.get("questionnaireScore"), 0));
                    row.put("questionnaire_passed", orDefault(u.get("questionnairePassed"), false));
                    row.put("questionnaire_review_summary", u.get("questionnaireReviewSummary"));
                    row.put("questionnaire_scored_at", u.get("questionnaireScoredAt"));
                    row.put("questionnaire_reasons", u.get("questionnaireReasons"));
                    row.put("questionnaire_answers", u.get("questionnaireAnswers"));
                    row.put("minecraft_uuid", u.get("minecraftUuid"));
                    row.put("minecraft_name", u.get("minecraftName"));
                    row.put("microsoft_verified", orDefault(u.get("microsoftVerified"), false));
                    row.put("verified_at", u.get("verifiedAt"));
                    row.put("verify_type", u.get("verifyType"));
                    row.put("invited_by", u.get("invitedBy"));
                    row.put("bedrock_uuid", u.get("bedrockUuid"));
                    row.put("bedrock_name", u.get("bedrockName"));
                    row.put("bedrock_verified", orDefault(u.get("bedrockVerified"), false));
                    row.put("bedrock_verified_at", u.get("bedrockVerifiedAt"));
                    row.put("ban_reason", u.get("banReason"));
                    row.put("ban_time", u.get("banTime"));
                    row.put("avatar", u.get("avatar"));
                    insert("dp_user", row);
                    users++;
                }
                report.add("users.json 导入 " + users + " 用户");
            }
            if (java.nio.file.Files.exists(auditPath)) {
                List<Object> data = mapper2.readValue(java.nio.file.Files.readString(auditPath), List.class);
                for (Object o : data) {
                    Map<String, Object> a = (Map<String, Object>) o;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("action", orDefault(a.get("action"), "unknown"));
                    row.put("operator", orDefault(a.get("operator"), "system"));
                    row.put("target", a.get("target"));
                    row.put("detail", a.get("detail"));
                    row.put("created_at", orDefault(a.get("timestamp"), System.currentTimeMillis()));
                    insert("dp_audit_log", row);
                    audits++;
                }
                report.add("audits.json 导入 " + audits + " 条审计");
            }
            if (report.isEmpty()) {
                report.add("未发现 users.json/audits.json");
            }
        } else {
            report.add("未配置 legacy-dir，跳过 JSON 导入");
        }
        return new JsonImportResult(users, audits, report);
    }

    private boolean userRepositoryExists(Map<String, Object> u) {
        String username = String.valueOf(firstNonNull(u.get("username"), ""));
        if (username.isBlank()) {
            return false;
        }
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dp_user WHERE LOWER(username) = LOWER(?)", Integer.class, username);
        return count != null && count > 0;
    }

    private void insert(String table, Map<String, Object> row) {
        List<String> cols = new ArrayList<>(row.keySet());
        String colSql = String.join(", ", cols);
        String placeholders = String.join(", ", cols.stream().map(c -> "?").toList());
        jdbc.update("INSERT INTO " + table + " (" + colSql + ") VALUES (" + placeholders + ")",
                cols.stream().map(row::get).toArray());
    }

    private boolean tableExists(String name) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class, name.toLowerCase());
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    boolean dpUserEmpty() {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dp_user", Integer.class);
            return count == null || count == 0;
        } catch (Exception e) {
            return true;
        }
    }

    private Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private Object orDefault(Object v, Object def) {
        return v != null ? v : def;
    }
}
