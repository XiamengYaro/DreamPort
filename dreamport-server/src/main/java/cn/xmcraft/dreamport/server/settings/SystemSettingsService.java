package cn.xmcraft.dreamport.server.settings;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 系统设置统一读写层：从 dp_setting 读取全部管理端可配置项。 */
@Service
public class SystemSettingsService {

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
