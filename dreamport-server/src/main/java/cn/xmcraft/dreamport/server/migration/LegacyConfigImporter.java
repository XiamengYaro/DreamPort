package cn.xmcraft.dreamport.server.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧 config.yml → dp_setting 导入（门户/背景/公告/管理员名单/下载中心/通知邮箱）。
 * SMTP/MySQL 等基础设施项打印为 application.yml 配置建议（不自动写入，避免误覆盖凭据）。
 */
public class LegacyConfigImporter {

    private static final Logger log = LoggerFactory.getLogger(LegacyConfigImporter.class);

    private final JdbcTemplate jdbc;

    public LegacyConfigImporter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> importConfig(String path) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        if (path == null || path.isBlank() || !java.nio.file.Files.exists(java.nio.file.Path.of(path))) {
            report.put("skipped", "未提供旧 config.yml 路径");
            return report;
        }
        Map<String, Object> cfg = new Yaml().load(java.nio.file.Files.readString(java.nio.file.Path.of(path)));
        if (cfg == null) {
            report.put("skipped", "config.yml 为空");
            return report;
        }
        importSetting("portal.config", asMap(cfg.get("portal")));
        importSetting("background.config", asMap(cfg.get("background")));
        importSetting("announcement", cfg.get("announcement"));
        importSetting("admins.list", cfg.get("admins"));
        importSetting("downloads.list", cfg.get("downloads"));
        report.put("importedKeys", List.of("portal.config", "background.config", "announcement",
                "admins.list", "downloads.list"));
        report.put("infraAdvice", List.of(
                "smtp.host=" + cfg.getOrDefault("smtp.host", "") + "（请手工写入 application-local.yml 的 wl.mail.host）",
                "mysql.database=" + cfg.getOrDefault("mysql.database", "") + "（请手工核对 WL_DB_* 环境变量）"));
        log.info("[迁移] config.yml 站点内容已导入 dp_setting；基础设施项请按建议手工确认");
        return report;
    }

    private void importSetting(String key, Object value) {
        if (value == null) {
            return;
        }
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
            jdbc.update("DELETE FROM dp_setting WHERE skey = ?", key);
            jdbc.update("INSERT INTO dp_setting (skey, svalue, updated_at, updated_by) VALUES (?, ?, ?, ?)",
                    key, json, System.currentTimeMillis(), "migrator");
        } catch (Exception e) {
            log.warn("[迁移] 设置导入失败 {}: {}", key, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }
}
