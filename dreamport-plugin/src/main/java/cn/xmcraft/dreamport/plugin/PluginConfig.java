package cn.xmcraft.dreamport.plugin;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 插件配置（对应 config.yml）。
 */
public record PluginConfig(
        String role,
        String backendUrl,
        String serverId,
        String serverToken,
        String failPolicy,
        int cacheTtlSeconds,
        int timeoutMs,
        boolean enforceWhitelist,
        boolean forwardChat,
        boolean reportJoinQuit
) {

    public static PluginConfig load(FileConfiguration config) {
        return new PluginConfig(
                config.getString("role", "primary"),
                trimTrailingSlash(config.getString("backend.url", "http://127.0.0.1:18898")),
                config.getString("backend.server-id", "main"),
                config.getString("backend.server-token", ""),
                config.getString("check.fail-policy", "cache").toLowerCase(),
                config.getInt("check.cache-ttl-seconds", 60),
                config.getInt("check.timeout-ms", 1500),
                config.getBoolean("features.enforce-whitelist", true),
                config.getBoolean("features.forward-chat", false),
                config.getBoolean("features.report-join-quit", false)
        );
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
