package cn.xmcraft.dreamport.server.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧版 config.yml 自动迁移（v0.5.9）：
 * v0.5.6 起业务设置（注册/AI评分/邀请/问卷/游戏/管理员通知邮箱）存入数据库 dp_setting
 * （管理面板热生效），config.yml 中的同名段失效。本迁移器在启动时：
 * 1. 检测 config.yml 中的失效段，把其中【数据库尚未配置】的值迁移进 dp_setting
 *    （数据库已有值 = 管理员已在面板配置，以面板为准）
 * 2. 备份原文件为 config.yml.backup-<时间戳>
 * 3. 重写 config.yml 移除失效段（幂等：下次启动无失效段则不动作）
 */
@Component
public class ConfigFileMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileMigrator.class);

    private final SystemSettingsService systemSettings;
    private final SettingService settingService;

    public ConfigFileMigrator(SystemSettingsService systemSettings, SettingService settingService) {
        this.systemSettings = systemSettings;
        this.settingService = settingService;
    }

    /** 旧段名 → (dp_setting 键, 旧键→新键映射) */
    @SuppressWarnings("unchecked")
    @Override
    public void run(ApplicationArguments args) {
        Path file = Path.of("config.yml");
        if (!Files.exists(file)) {
            return;
        }
        try {
            Map<String, Object> root = new Yaml().load(Files.readString(file, StandardCharsets.UTF_8));
            if (root == null || !(root.get("wl") instanceof Map)) {
                return;
            }
            Map<String, Object> wl = (Map<String, Object>) root.get("wl");

            List<String> migrated = new ArrayList<>();
            List<String> skipped = new ArrayList<>();

            // ---- wl.register → register.config ----
            if (wl.get("register") instanceof Map) {
                Map<String, Object> section = (Map<String, Object>) wl.remove("register");
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapValue(section, "require-email-code", "requireEmailCode", mapped);
                mapValue(section, "captcha-enabled", "captchaEnabled", mapped);
                mapValue(section, "max-accounts-per-email", "maxAccountsPerEmail", mapped);
                mapValue(section, "domain-whitelist-enabled", "domainWhitelistEnabled", mapped);
                mapValue(section, "email-domain-whitelist", "emailDomainWhitelist", mapped);
                mapValue(section, "auto-approve", "autoApprove", mapped);
                mergeInto(SettingService.KEY_REGISTER_CONFIG, mapped, migrated, skipped);
            }
            // ---- wl.llm → llm.config ----
            if (wl.get("llm") instanceof Map) {
                Map<String, Object> section = (Map<String, Object>) wl.remove("llm");
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapValue(section, "enabled", "enabled", mapped);
                mapValue(section, "api-base", "apiBase", mapped);
                mapValue(section, "api-key", "apiKey", mapped);
                mapValue(section, "model", "model", mapped);
                mapValue(section, "timeout-ms", "timeoutMs", mapped);
                mapValue(section, "max-concurrency", "maxConcurrency", mapped);
                mapValue(section, "system-prompt", "systemPrompt", mapped);
                mergeInto(SettingService.KEY_LLM_CONFIG, mapped, migrated, skipped);
            }
            // ---- wl.invite → invite.config ----
            if (wl.get("invite") instanceof Map) {
                Map<String, Object> section = (Map<String, Object>) wl.remove("invite");
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapValue(section, "enabled", "enabled", mapped);
                mapValue(section, "code-expiry-days", "codeExpiryDays", mapped);
                mapValue(section, "max-invites-per-user", "maxInvitesPerUser", mapped);
                mergeInto(SettingService.KEY_INVITE_CONFIG, mapped, migrated, skipped);
            }
            // ---- wl.questionnaire → questionnaire.config ----
            if (wl.get("questionnaire") instanceof Map) {
                Map<String, Object> section = (Map<String, Object>) wl.remove("questionnaire");
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapValue(section, "enabled", "enabled", mapped);
                mapValue(section, "pass-score", "passScore", mapped);
                mergeInto(SettingService.KEY_QUESTIONNAIRE_CONFIG, mapped, migrated, skipped);
            }
            // ---- wl.admin-notify-email → mail.admin_notify_email ----
            if (wl.containsKey("admin-notify-email")) {
                Object v = wl.remove("admin-notify-email");
                if (v != null && !String.valueOf(v).isBlank()) {
                    String existing = settingService.get(SettingService.KEY_ADMIN_NOTIFY_EMAIL, String.class);
                    if (existing == null || existing.isBlank()) {
                        settingService.set(SettingService.KEY_ADMIN_NOTIFY_EMAIL, v);
                        migrated.add("admin-notify-email → mail.admin_notify_email");
                    } else {
                        skipped.add("admin-notify-email（面板已配置）");
                    }
                }
            }
            // ---- wl.web-register-url → game.config.webRegisterUrl ----
            if (wl.containsKey("web-register-url")) {
                Object v = wl.remove("web-register-url");
                if (v != null && !String.valueOf(v).isBlank()) {
                    Map<String, Object> game = systemSettings.gameConfig();
                    Map<String, Object> raw = settingService.getMap(SettingService.KEY_GAME_CONFIG);
                    if (raw.isEmpty() || isBlank(raw.get("webRegisterUrl"))) {
                        game.put("webRegisterUrl", String.valueOf(v));
                        systemSettings.saveGameConfig(game);
                        migrated.add("web-register-url → game.config.webRegisterUrl");
                    } else {
                        skipped.add("web-register-url（面板已配置）");
                    }
                }
            }

            if (migrated.isEmpty() && skipped.isEmpty()) {
                return; // 无失效段，幂等退出
            }

            // ---- 备份 + 重写 ----
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            Path backup = Path.of("config.yml.backup-" + ts);
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setIndent(2);
            String body = new Yaml(options).dump(root);
            String header = "# ============================================================\n"
                    + "#   DreamPort 部署配置（v0.5.9 起仅保留基础设施项）\n"
                    + "#   业务设置（注册/AI评分/邀请/问卷/游戏/管理员通知邮箱）已迁移至\n"
                    + "#   数据库，请在管理后台「外观设置」页配置（热生效）。\n"
                    + "#   本次迁移前的原文件已备份为: " + backup.getFileName() + "\n"
                    + "# ============================================================\n\n";
            Files.writeString(file, header + body, StandardCharsets.UTF_8);

            log.info("[配置迁移] config.yml 业务段已迁移至数据库并重写文件（备份: {}）", backup.getFileName());
            for (String m : migrated) {
                log.info("[配置迁移] 已迁移: {}", m);
            }
            for (String sk : skipped) {
                log.info("[配置迁移] 跳过（面板已配置）: {}", sk);
            }
        } catch (Exception e) {
            log.error("[配置迁移] config.yml 迁移失败（不影响启动，文件保持原样）: {}", e.getMessage(), e);
        }
    }

    private void mapValue(Map<String, Object> section, String oldKey, String newKey, Map<String, Object> out) {
        if (section.containsKey(oldKey)) {
            out.put(newKey, section.get(oldKey));
        }
    }

    /** dp_setting 无该键原始数据时才写入（面板为最高优先级）；raw 已存在则跳过 */
    private void mergeInto(String targetKey, Map<String, Object> values,
                           List<String> migrated, List<String> skipped) {
        if (values.isEmpty()) {
            return;
        }
        Map<String, Object> raw = settingService.getMap(targetKey);
        if (!raw.isEmpty()) {
            skipped.add(targetKey + "（面板已配置）");
            return;
        }
        // 与默认值合并，保证键完整
        Map<String, Object> merged = switch (targetKey) {
            case SettingService.KEY_REGISTER_CONFIG -> systemSettings.registerConfig();
            case SettingService.KEY_LLM_CONFIG -> systemSettings.llmConfig();
            case SettingService.KEY_INVITE_CONFIG -> systemSettings.inviteConfig();
            case SettingService.KEY_QUESTIONNAIRE_CONFIG -> systemSettings.questionnaireConfig();
            default -> new LinkedHashMap<>(values);
        };
        merged.putAll(values);
        settingService.set(targetKey, merged);
        migrated.add(targetKey + " ← " + values.keySet());
    }

    private boolean isBlank(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }
}
