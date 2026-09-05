package cn.xmcraft.dreamport.server.config;

import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;

/**
 * 自定义启动记录（替代 Spring 原生启动日志）：
 * main() 打印字符画 Banner → 各阶段事件收集 → ApplicationReady 输出中文启动清单。
 * 框架日志已由 logback-spring.xml 压制为 WARN，仅应用日志与告警可见。
 */
@Component
public class StartupReporter {

    private static final Logger log = LoggerFactory.getLogger(StartupReporter.class);

    /** main() 里赋值，用于计算启动耗时 */
    public static long START_NANOS;

    private final WlProps props;
    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final SettingService settingService;
    private final cn.xmcraft.dreamport.server.questionnaire.LlmScoringClient llmClient;
    private final cn.xmcraft.dreamport.server.infra.MailService mailService;

    private volatile int webPort = -1;

    public StartupReporter(WlProps props, JdbcTemplate jdbc, UserRepository userRepository,
                           SettingService settingService,
                           cn.xmcraft.dreamport.server.questionnaire.LlmScoringClient llmClient,
                           cn.xmcraft.dreamport.server.infra.MailService mailService) {
        this.props = props;
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.settingService = settingService;
        this.llmClient = llmClient;
        this.mailService = mailService;
    }

    /** 字符画 Banner（仅用 █ 与空格，任何终端字体都能正确显示） */
    public static void printBanner() {
        System.out.println();
        String[][] lines = {
            {"█████ ","████ ","█████ ","█████ ","█   █ ","████ ","█████ ","████ ","█████ "},
            {"█   █ ","█   █ ","█     ","█   █ ","██ ██ ","█   █ ","█   █ ","█   █ ","  █  "},
            {"█   █ ","████ ","███   ","█████ ","█ █ █ ","████ ","█   █ ","████ ","  █  "},
            {"█   █ ","█  █ ","█     ","█   █ ","█   █ ","█     ","█   █ ","█  █ ","  █  "},
            {"█████ ","█   █ ","█████ ","█   █ ","█   █ ","█     ","█████ ","█   █ ","  █  "}
        };
        for (String[] row : lines) {
            StringBuilder sb = new StringBuilder("\u001B[38;5;208m");
            for (String cell : row) {
                sb.append(cell).append(' ');
            }
            System.out.println(sb);
        }
        System.out.println("\u001B[38;5;250m   夏日小镇 · 梦港  ——  Minecraft 服务器门户与玩家管理系统\u001B[0m");
        System.out.println();
    }

    @EventListener
    public void onWebServer(WebServerInitializedEvent event) {
        webPort = event.getWebServer().getPort();
        log.info("✓ Web 服务就绪，端口 {}（REST + 管理后台 + SPA）", webPort);
    }

    @EventListener
    public void onReady(ApplicationReadyEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append("┌─────────────────────────── 启动记录 ───────────────────────────┐\n");

        sb.append(row("数据库", describeDb()));

        int tables = countDpTables();
        sb.append(row("数据表", tables + " 张（dp_ 前缀，Flyway 管理）"));

        Map<String, Object> report = settingService.getMap("migration.report");
        Object imported = report.get("importedRows");
        if (imported instanceof Map<?, ?> m && !m.isEmpty()) {
            int total = m.values().stream().filter(v -> v instanceof Number)
                    .mapToInt(v -> ((Number) v).intValue()).sum();
            sb.append(row("旧库迁移", "已导入 " + total + " 行历史数据（" + m.size() + " 张表）"));
        } else {
            sb.append(row("旧库迁移", "未检测到旧版数据（全新部署）"));
        }

        long users = userRepository.count();
        sb.append(row("初始化", users == 0
                ? "待初始化 → 访问 /setup 创建管理员（全新部署或导入旧库）"
                : "已完成（用户 " + users + " 名，管理员名单 " + adminCount() + " 人）"));

        boolean llmOn = llmClient.enabled();
        sb.append(row("问卷 AI 评分", llmOn ? "已启用（" + props.llm().model() + "）" : "未启用（文本题按长度降级评分）"));
        sb.append(row("邮件服务", mailService.configured()
                ? "SMTP：" + props.mail().host() : "日志模式（SMTP 未配置，验证码打印在日志）"));
        sb.append(row("邀请系统", props.invite().enabled()
                ? "已启用（每人 " + props.invite().maxInvitesPerUser() + " 个活跃码 / " + props.invite().codeExpiryDays() + " 天有效）"
                : "未启用"));
        sb.append(row("维护模式", settingService.getBool(SettingService.KEY_MAINTENANCE, false) ? "⚠ 开启中" : "关闭"));

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576;
        sb.append(row("运行环境", "Java " + System.getProperty("java.version")
                + " ｜ 内存 " + usedMb + "MB"));

        double seconds = (System.nanoTime() - START_NANOS) / 1_000_000_000.0;
        sb.append(row("启动耗时", String.format("%.1f 秒（版本 %s）", seconds, props.version())));

        sb.append("└────────────────────────────────────────────────────────────────┘\n");
        sb.append('\n');
        String host = "localhost";
        sb.append("  \u001B[38;5;208m官网 / 玩家中心\u001B[0m   http://").append(host).append(':').append(webPort).append('\n');
        sb.append("  \u001B[38;5;208m管理后台\u001B[0m         http://").append(host).append(':').append(webPort).append("/admin\n");
        if (users == 0) {
            sb.append("  \u001B[38;5;208m初始化向导\u001B[0m       http://").append(host).append(':').append(webPort).append("/setup\n");
        }
        sb.append('\n');
        System.out.print(sb);
        log.info("DreamPort 启动完成（{}s）", String.format("%.1f", seconds));
    }

    @EventListener
    public void onFailed(ApplicationFailedEvent event) {
        Throwable ex = event.getException();
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        System.out.println();
        System.out.println("✗ 启动失败：" + root.getMessage());
        System.out.println("  常见原因：MySQL 未启动 / config.yml 数据库连接信息有误 / 端口被占用");
        System.out.println("  详细堆栈见上方 ERROR 日志");
    }

    // ---------- 工具 ----------

    private String describeDb() {
        try {
            DataSource ds = jdbc.getDataSource();
            if (ds == null) {
                return "未连接";
            }
            try (Connection conn = ds.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                String url = meta.getURL();
                int q = url.indexOf('?');
                if (q > 0) {
                    url = url.substring(0, q);
                }
                return meta.getDatabaseProductName() + " ｜ " + url.replace("jdbc:mysql://", "");
            }
        } catch (Exception e) {
            return "连接异常：" + e.getMessage();
        }
    }

    private int countDpTables() {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'dp\\_%'",
                    Integer.class);
            return count == null ? 0 : count;
        } catch (Exception e) {
            return 0;
        }
    }

    private int adminCount() {
        List<Object> admins = settingService.get(SettingService.KEY_ADMINS, List.class);
        return admins == null ? 0 : admins.size();
    }

    private String row(String key, String value) {
        int pad = Math.max(1, 20 - key.length() - countCjk(key));
        return "│ ✓ " + key + " ".repeat(pad) + value + "\n";
    }

    private int countCjk(String s) {
        int n = 0;
        for (char c : s.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {
                n++;
            }
        }
        return n;
    }
}
