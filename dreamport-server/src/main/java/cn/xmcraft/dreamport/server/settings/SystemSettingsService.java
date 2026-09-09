package cn.xmcraft.dreamport.server.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 系统设置统一读写层：从 dp_setting 读取全部管理端可配置项。 */
@Service
public class SystemSettingsService {

    private final ObjectMapper mapper = new ObjectMapper();

    private final SettingService settingService;

    public SystemSettingsService(SettingService settingService) {
        this.settingService = settingService;
    }

    public Map<String, Object> registerConfig() {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("requireEmailCode", true);
        def.put("captchaEnabled", false);
        def.put("maxAccountsPerEmail", 2);
        def.put("domainWhitelistEnabled", true);
        def.put("emailDomainWhitelist", List.of("qq.com", "163.com", "gmail.com", "outlook.com", "xmcraft.cn"));
        def.put("autoApprove", false);
        def.put("usernameRegex", "^[a-zA-Z0-9_-]{3,16}$");
        return merge(SettingService.KEY_REGISTER_CONFIG, def);
    }

    public Map<String, Object> llmConfig() {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("enabled", false);
        def.put("apiBase", "https://api.deepseek.com/v1");
        def.put("apiKey", "");
        def.put("model", "deepseek-chat");
        def.put("timeoutMs", 10000);
        def.put("maxConcurrency", 4);
        def.put("systemPrompt", "你是一位公正的 Minecraft 白名单问卷评分员。严格根据问题、候选人回答和评分规则进行评分。仅返回 JSON。");
        return merge(SettingService.KEY_LLM_CONFIG, def);
    }

    /** 问卷设置（启用开关 + 默认及格分） */
    public Map<String, Object> questionnaireConfig() {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("enabled", true);
        def.put("passScore", 60);
        return merge(SettingService.KEY_QUESTIONNAIRE_CONFIG, def);
    }

    public void saveQuestionnaireConfig(Map<String, Object> config) {
        settingService.set(SettingService.KEY_QUESTIONNAIRE_CONFIG, config);
    }

    public Map<String, Object> inviteConfig() {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("enabled", true);
        def.put("codeExpiryDays", 7);
        def.put("maxInvitesPerUser", 3);
        return merge(SettingService.KEY_INVITE_CONFIG, def);
    }

    public Map<String, Object> gameConfig() {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("whitelistMode", "plugin");
        def.put("webRegisterUrl", "");
        def.put("bedrockEnabled", false);
        def.put("bedrockPrefix", ".");
        return merge(SettingService.KEY_GAME_CONFIG, def);
    }

    public Map<String, Object> portalConfig() {
        return settingService.getMap(SettingService.KEY_PORTAL);
    }

    public Map<String, Object> downloads() {
        return settingService.getMap(SettingService.KEY_DOWNLOADS);
    }

    public void saveRegisterConfig(Map<String, Object> config) {
        settingService.set(SettingService.KEY_REGISTER_CONFIG, config);
    }

    public void saveLlmConfig(Map<String, Object> config) {
        settingService.set(SettingService.KEY_LLM_CONFIG, config);
    }

    public void saveInviteConfig(Map<String, Object> config) {
        settingService.set(SettingService.KEY_INVITE_CONFIG, config);
    }

    public void saveGameConfig(Map<String, Object> config) {
        settingService.set(SettingService.KEY_GAME_CONFIG, config);
    }

    /** 公告页(资讯中心 + 更新日志):两个 JSON 数组键 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> announcementsConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> news = settingService.get(SettingService.KEY_NEWS, List.class);
        List<Map<String, Object>> changelog = settingService.get(SettingService.KEY_CHANGELOG, List.class);
        result.put("news", news == null ? List.of() : news);
        result.put("changelog", changelog == null ? List.of() : changelog);
        return result;
    }

    public void saveAnnouncementsConfig(Map<String, Object> config) {
        if (config.containsKey("news")) {
            settingService.set(SettingService.KEY_NEWS,
                    config.get("news") == null ? List.of() : config.get("news"));
        }
        if (config.containsKey("changelog")) {
            settingService.set(SettingService.KEY_CHANGELOG,
                    config.get("changelog") == null ? List.of() : config.get("changelog"));
        }
    }

    /** QQ 互通（AstrBot）：enabled / api_token / 群绑定 JSON 分键存储，AstrBotController 与 QqBridgeService 直接按键读取 */
    public Map<String, Object> astrbotConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", settingService.getBool(SettingService.KEY_ASTRBOT_ENABLED, false));
        String token = settingService.get(SettingService.KEY_ASTRBOT_TOKEN, String.class);
        result.put("apiToken", token == null ? "" : token);
        String bindings = settingService.getRaw(cn.xmcraft.dreamport.server.qq.QqBridgeService.KEY_GROUP_BINDINGS);
        result.put("groupBindings", bindings == null || bindings.isBlank() ? "[]" : bindings);
        return result;
    }

    public void saveAstrbotConfig(Map<String, Object> config) {
        if (config.containsKey("enabled")) {
            settingService.set(SettingService.KEY_ASTRBOT_ENABLED,
                    Boolean.TRUE.equals(config.get("enabled")) || "true".equalsIgnoreCase(String.valueOf(config.get("enabled"))));
        }
        if (config.containsKey("apiToken")) {
            Object token = config.get("apiToken");
            settingService.set(SettingService.KEY_ASTRBOT_TOKEN, token == null ? "" : String.valueOf(token));
        }
        if (config.containsKey("groupBindings")) {
            Object raw = config.get("groupBindings");
            if (raw == null || String.valueOf(raw).isBlank()) {
                settingService.set(cn.xmcraft.dreamport.server.qq.QqBridgeService.KEY_GROUP_BINDINGS, List.of());
                return;
            }
            try {
                // 前端传原始 JSON 文本:解析为列表后按正规 JSON 数组落库(QqBridgeService 按列表读取)
                List<?> parsed = new ObjectMapper().readValue(String.valueOf(raw), List.class);
                settingService.set(cn.xmcraft.dreamport.server.qq.QqBridgeService.KEY_GROUP_BINDINGS, parsed);
            } catch (Exception e) {
                throw new IllegalArgumentException("群绑定 JSON 格式错误");
            }
        }
    }

    /** 注册守则:doc=守则文档(分类/文件名.md 或 文件名.md,空=未启用),seconds=强制阅读秒数 */
    public Map<String, Object> rulesConfig() {
        Map<String, Object> m = settingService.getMap(SettingService.KEY_RULES_CONFIG);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("doc", m.getOrDefault("doc", ""));
        result.put("seconds", m.getOrDefault("seconds", 15));
        return result;
    }

    public void saveRulesConfig(Map<String, Object> config) {
        Map<String, Object> m = new LinkedHashMap<>();
        Object doc = config.get("doc");
        m.put("doc", doc == null ? "" : String.valueOf(doc).trim());
        Object seconds = config.get("seconds");
        int sec = 15;
        try {
            sec = Math.max(0, Integer.parseInt(String.valueOf(seconds)));
        } catch (NumberFormatException ignored) {
        }
        m.put("seconds", sec);
        settingService.set(SettingService.KEY_RULES_CONFIG, m);
    }

    /** 任务规则(tasks.config:{"tasks":[{id,type,channel,period,name,desc,target,points,enabled}]}) */
    public Map<String, Object> tasksConfig() {
        // getRaw 取原始 JSON(get(String.class) 对 JSON 对象抛异常返回 null,配置永不生效——遗留 bug)
        String raw = settingService.getRaw(SettingService.KEY_TASKS_CONFIG);
        if (raw == null || raw.isBlank()) {
            return Map.of("tasks", List.of());
        }
        try {
            return mapper.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("tasks", List.of());
        }
    }

    public void saveTasksConfig(Map<String, Object> config) {
        settingService.set(SettingService.KEY_TASKS_CONFIG, config);
    }

    /** 兑换商店(shop.config:{"rewards":[{id,name,desc,cost,commands,enabled}]}) */
    public Map<String, Object> shopConfig() {
        // getRaw 取原始 JSON(同上,get(String.class) 对 JSON 对象永不生效)
        String raw = settingService.getRaw(SettingService.KEY_SHOP_CONFIG);
        if (raw == null || raw.isBlank()) {
            return Map.of("rewards", List.of());
        }
        try {
            return mapper.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("rewards", List.of());
        }
    }

    public void saveShopConfig(Map<String, Object> config) {
        settingService.set(SettingService.KEY_SHOP_CONFIG, config);
    }

    public void saveDownloads(Map<String, Object> downloads) {
        settingService.set(SettingService.KEY_DOWNLOADS, downloads);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> merge(String key, Map<String, Object> defaults) {
        Map<String, Object> stored = settingService.getMap(key);
        Map<String, Object> result = new LinkedHashMap<>(defaults);
        if (stored != null) result.putAll(stored);
        return result;
    }
}
