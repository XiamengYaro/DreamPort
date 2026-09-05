package cn.xmcraft.dreamport.server.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时自动触发迁移（wl.migration.enabled 默认 true；仅当检测到旧表且 dp_user 为空时执行）。
 */
@Component
@ConditionalOnProperty(name = "wl.migration.enabled", havingValue = "true", matchIfMissing = true)
public class MigrationStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationStartupRunner.class);

    private final JdbcTemplate jdbc;
    private final org.springframework.core.env.Environment env;

    public MigrationStartupRunner(JdbcTemplate jdbc, org.springframework.core.env.Environment env) {
        this.jdbc = jdbc;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String legacyDir = env.getProperty("wl.migration.legacy-dir", "");
            String legacyConfig = env.getProperty("wl.migration.legacy-config", "");
            new LegacyMigrator(jdbc).migrateAll(legacyDir, legacyConfig);
        } catch (Exception e) {
            log.error("[迁移] 启动迁移失败（不影响服务运行）: {}", e.getMessage(), e);
        }
    }
}
