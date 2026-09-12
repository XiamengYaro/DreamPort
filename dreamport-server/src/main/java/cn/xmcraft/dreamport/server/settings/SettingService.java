package cn.xmcraft.dreamport.server.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 站点设置 KV 存储（dp_setting）——替代旧版写 config.yml 的站点内容
 * （门户/背景/公告/维护模式/验证页/管理员名单/通知邮箱等，Rules.md §9-6/7）。
 * 值统一存 JSON 字符串，读取时反序列化。
 */
@Service
public class SettingService {

    public static final String KEY_MAINTENANCE = "maintenance.enabled";
    public static final String KEY_ADMINS = "admins.list";
    public static final String KEY_ADMIN_NOTIFY_EMAIL = "mail.admin_notify_email";
    public static final String KEY_PORTAL = "portal.config";
    public static final String KEY_BACKGROUND = "background.config";
    public static final String KEY_ANNOUNCEMENT = "announcement";
    public static final String KEY_DOWNLOADS = "downloads.list";
    public static final String KEY_ECONOMY_SNAPSHOT = "economy.snapshot";
    public static final String KEY_CHAT_HISTORY = "chat.history";
    public static final String KEY_REGISTER_CONFIG = "register.config";
    public static final String KEY_LLM_CONFIG = "llm.config";
    public static final String KEY_INVITE_CONFIG = "invite.config";
    public static final String KEY_GAME_CONFIG = "game.config";
    public static final String KEY_SETUP_COMPLETED = "setup.completed";
    public static final String KEY_QUESTIONNAIRE_CONFIG = "questionnaire.config";
    public static final String KEY_NEWS = "news.list";
    public static final String KEY_CHANGELOG = "changelog.list";
    public static final String KEY_ASTRBOT_ENABLED = "astrbot.enabled";
    public static final String KEY_ASTRBOT_TOKEN = "astrbot.api_token";
    public static final String KEY_RULES_CONFIG = "rules.config";
    public static final String KEY_TASKS_CONFIG = "tasks.config";
    public static final String KEY_TITLES_CONFIG = "titles.config";
    public static final String KEY_ACHIEVEMENTS_CONFIG = "achievements.config";
    public static final String KEY_SHOP_CONFIG = "shop.config";
    /** 鉴权配置:{tokenMode: shared|per_server, admin2faRequired: bool}(阶段 C 补 admin2faRequired) */
    public static final String KEY_SECURITY_CONFIG = "security.config";
    /** 资源监控配置:{tpsAlertEnabled: bool, tpsThreshold: number} */
    public static final String KEY_METRICS_CONFIG = "metrics.config";
    /** 行为分析配置:{publicScore: bool, 各项权重与封顶值} */
    public static final String KEY_ANALYTICS_CONFIG = "analytics.config";
    /** 站点 SEO 配置:{titleTemplate, description, keywords, ogImage, robots: index|noindex, extraHead} */
    public static final String KEY_SEO_CONFIG = "seo.config";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public SettingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void set(String key, Object value) {
        try {
            String json = value == null ? null : mapper.writeValueAsString(value);
            // 修复审计 M5：删除改参数化（此前手工转义拼接，key 一旦用户可控即成注入点）
            jdbc.update("DELETE FROM dp_setting WHERE skey = ?", key);
            jdbc.update("INSERT INTO dp_setting (skey, svalue, updated_at, updated_by) VALUES (?, ?, ?, ?)",
                    key, json, System.currentTimeMillis(), "system");
        } catch (Exception e) {
            throw new IllegalStateException("写入设置失败: " + key, e);
        }
    }

    /** @return 原始 JSON 字符串；不存在返回 null */
    public String getRaw(String key) {
        var list = jdbc.queryForList("SELECT svalue FROM dp_setting WHERE skey = ?", String.class, key);
        return list.isEmpty() ? null : list.get(0);
    }

    public <T> T get(String key, Class<T> type) {
        String raw = getRaw(key);
        if (raw == null) {
            return null;
        }
        try {
            return mapper.readValue(raw, type);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        String raw = getRaw(key);
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(raw, LinkedHashMap.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public boolean getBool(String key, boolean def) {
        String raw = getRaw(key);
        if (raw == null) {
            return def;
        }
        return Boolean.parseBoolean(raw.replace("\"", ""));
    }
}
