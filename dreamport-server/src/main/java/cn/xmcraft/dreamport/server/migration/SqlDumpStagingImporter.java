package cn.xmcraft.dreamport.server.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * mysqldump 格式旧库上传导入器（管理后台上传 → 暂存表）。
 *
 * 流程：解析 .sql 文本 → 仅提取 9 张旧版表的 CREATE/INSERT/DROP →
 * 表名重写为 dp_migration_staging_* 暂存表（隔离，不触碰现网表）→ 执行建表与数据装载。
 * 仅支持 MySQL：dump 语句原样执行（仅重写表名到暂存前缀），
 * 跳过 LOCK/UNLOCK 与 /*!…*​/ 条件指令。
 */
@Service
public class SqlDumpStagingImporter {

    private static final Logger log = LoggerFactory.getLogger(SqlDumpStagingImporter.class);
    public static final String STAGING_PREFIX = "dp_migration_staging_";

    /** 旧版 9 表（与 LegacyMigrator.TABLE_MAP 的键一致） */
    public static final Set<String> LEGACY_TABLES = Set.of(
            "xmwhitelist_users", "xmwhitelist_audits", "xmwhitelist_invites",
            "xmwhitelist_notifications", "xmwhitelist_pending_logins",
            "xmwhitelist_password_resets", "xmwhitelist_appeals",
            "village_trades", "public_machines");

    private final JdbcTemplate jdbc;

    public SqlDumpStagingImporter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record StagedTable(String originalName, String stagingName, int rows) {
    }

    public record StagingOutcome(List<StagedTable> staged, int statementsExecuted) {
    }

    /** 清空（删除）暂存表 */
    public void dropStaging() {
        for (String table : LEGACY_TABLES) {
            try {
                jdbc.execute("DROP TABLE IF EXISTS " + STAGING_PREFIX + table);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 解析并装载 dump 到暂存表。
     *
     * @return 每张实际装载数据的暂存表及行数
     */
    public StagingOutcome importDump(String sql) {
        dropStaging();
        List<String> statements = splitStatements(sql);
        Map<String, Integer> stagedRows = new LinkedHashMap<>();
        int executed = 0;
        for (String raw : statements) {
            String statement = raw.trim();
            if (statement.isEmpty() || statement.startsWith("/*") || statement.startsWith("--")
                    || statement.startsWith("#")) {
                continue;
            }
            String upper = statement.toUpperCase();
            if (upper.startsWith("LOCK TABLES") || upper.startsWith("UNLOCK TABLES")) {
                continue;
            }
            String table = extractTable(statement, upper);
            if (table == null || !LEGACY_TABLES.contains(table)) {
                continue; // 只关心旧版 9 表，其余（商城/旧网站等）一律跳过
            }
            String staging = STAGING_PREFIX + table;
            String transformed = transform(statement, table, staging);
            if (transformed == null) {
                continue;
            }
            try {
                jdbc.execute(transformed);
                executed++;
                if (upper.startsWith("INSERT")) {
                    stagedRows.put(table, countTable(staging));
                }
            } catch (Exception e) {
                log.warn("暂存导入失败（{}）: {}", table, e.getMessage());
            }
        }
        List<StagedTable> staged = new ArrayList<>();
        for (Map.Entry<String, Integer> e : stagedRows.entrySet()) {
            staged.add(new StagedTable(e.getKey(), STAGING_PREFIX + e.getKey(), e.getValue()));
        }
        log.info("[上传迁移] 暂存装载完成：{} 张表，执行 {} 条语句", staged.size(), executed);
        return new StagingOutcome(staged, executed);
    }

    /** 按引号感知方式切分语句 */
    static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            current.append(c);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '\'') {
                inString = !inString;
                continue;
            }
            if (c == ';' && !inString) {
                out.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            out.add(current.toString());
        }
        return out;
    }

    private String extractTable(String statement, String upper) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:CREATE TABLE|INSERT INTO|DROP TABLE IF EXISTS|ALTER TABLE)\\s+`?(\\w+)`?",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(upper.startsWith("DROP") ? statement : statement);
        // 用原文匹配大小写敏感的表名即可，均小写
        m = java.util.regex.Pattern
                .compile("(?:CREATE TABLE|INSERT INTO|DROP TABLE IF EXISTS|ALTER TABLE)\\s+`(\\w+)`")
                .matcher(statement);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 表名重写 + 方言清洗：
     * - CREATE：剥离表选项（ENGINE/DEFAULT CHARSET/…）与列级 COLLATE
     * - INSERT/DROP：仅重写表名
     */
    String transform(String statement, String originalTable, String stagingTable) {
        // 仅重写表名（MySQL 原生执行，无需方言清洗）
        return statement.replace("`" + originalTable + "`", "`" + stagingTable + "`");
    }


    private int countTable(String staging) {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + staging, Integer.class);
            return count == null ? 0 : count;
        } catch (Exception e) {
            return 0;
        }
    }
}
