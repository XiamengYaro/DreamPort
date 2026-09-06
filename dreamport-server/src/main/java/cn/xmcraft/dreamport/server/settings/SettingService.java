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

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public SettingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void set(String key, Object value) {
        try {
            String json = value == null ? null : mapper.writeValueAsString(value);
            jdbc.execute("DELETE FROM dp_setting WHERE skey = '" + key.replace("'", "''") + "'");
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
