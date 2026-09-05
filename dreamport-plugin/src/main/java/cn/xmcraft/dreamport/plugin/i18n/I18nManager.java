package cn.xmcraft.dreamport.plugin.i18n;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 插件侧 i18n（键与旧版 login.* 兼容；后端 reasonKey 直接落到这里取文案）。
 */
public class I18nManager {

    private final Map<String, String> messages = new HashMap<>();
    private final DreamPortPlugin plugin;

    public I18nManager(DreamPortPlugin plugin, String language) {
        this.plugin = plugin;
        reload(language);
    }

    public void reload(String language) {
        messages.clear();
        String lang = "en".equalsIgnoreCase(language) ? "en" : "zh";
        load("i18n/messages_" + lang + ".properties");
        // 缺失键回退中文
        if (!"zh".equals(lang)) {
            load("i18n/messages_zh.properties");
        }
    }

    private void load(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                return;
            }
            Properties props = new Properties();
            props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            props.forEach((k, v) -> messages.putIfAbsent(String.valueOf(k), String.valueOf(v)));
        } catch (Exception e) {
            plugin.getLogger().warning("i18n 加载失败 " + path + ": " + e.getMessage());
        }
    }

    /** 支持旧版 &color 段落符；缺失键返回键名 */
    public String msg(String key) {
        String value = messages.getOrDefault(key, key);
        return value.replace('&', '\u00a7');
    }
}
