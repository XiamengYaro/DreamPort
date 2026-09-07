package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.settings.SystemSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点公开配置（契约对齐旧版 /api/config；P4 从 dp_setting 读取，P1 返回骨架值）。
 */
@RestController
@RequestMapping("/api")
public class ConfigController {

    private final SettingService settingService;
    private final SystemSettingsService systemSettings;

    public ConfigController(SettingService settingService, SystemSettingsService systemSettings) {
        this.settingService = settingService;
        this.systemSettings = systemSettings;
    }

    /** 站点公开配置（Portal/Admin/Dashboard 均读 data.portal / data.announcement / data.verifyPage / data.bedrockEnabled） */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> portal = settingService.getMap(SettingService.KEY_PORTAL);
        if (portal.isEmpty()) {
            portal.put("server_name", "夏日小镇");
            portal.put("subtitle", "Minecraft 服务器");
            portal.put("description", "一个有趣、友好的 Minecraft 生存服务器，欢迎每一位玩家加入！");
        }
        Object announcement = settingService.get(SettingService.KEY_ANNOUNCEMENT, String.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("portal", portal);
        data.put("serverName", portal.getOrDefault("server_name", "夏日小镇"));
        data.put("subtitle", portal.getOrDefault("subtitle", "Minecraft 服务器"));
        data.put("announcement", announcement == null ? "欢迎来到夏日小镇！" : announcement);
        // 背景设置(外观设置卡保存的 image/opacity/blur),App.vue applyBackground 消费
        data.put("background", settingService.getMap(SettingService.KEY_BACKGROUND));
        data.put("registerEnabled", true);
        data.put("authMethods", java.util.List.of("email"));
        data.put("bedrockEnabled", Boolean.TRUE.equals(
                systemSettings.gameConfig().getOrDefault("bedrockEnabled", false)));
        data.put("verifyPage", settingService.getMap("verify.config"));
        var qnCfg = systemSettings.questionnaireConfig();
        data.put("questionnaireEnabled", qnCfg.getOrDefault("enabled", true));
        data.put("questionnairePassScore", qnCfg.getOrDefault("passScore", 60));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }

    /** 公告页公开数据(资讯中心 + 更新日志;仅已发布且到时的) */
    @GetMapping("/announcements")
    public Map<String, Object> announcements() {
        long now = System.currentTimeMillis();
        var newsRaw = settingService.get(SettingService.KEY_NEWS, List.class);
        var visible = (newsRaw == null ? List.<Object>of() : newsRaw).stream()
                .filter(o -> {
                    if (!(o instanceof Map<?, ?> m)) return false;
                    String status = m.get("status") == null ? "published" : String.valueOf(m.get("status"));
                    if ("draft".equals(status)) return false;
                    Object pa = m.get("publishAt");
                    if (pa != null) {
                        long t = -1;
                        if (pa instanceof Number n) t = n.longValue();
                        else if (pa instanceof java.time.LocalDateTime pd)
                            t = pd.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                        else try { t = Long.parseLong(String.valueOf(pa)); } catch (NumberFormatException ignored) {}
                        if (t > now) return false;
                    }
                    return true;
                })
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("news", visible);
        data.put("changelog", settingService.get(SettingService.KEY_CHANGELOG, List.class));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }

}
