package cn.xmcraft.dreamport.plugin;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 插件配置（对应 config.yml）。
 */
public record PluginConfig(
        String role,
        String language,
        String backendUrl,
        String serverId,
        String serverName,
        String serverToken,
        String failPolicy,
        int cacheTtlSeconds,
        int timeoutMs,
        boolean enforceWhitelist,
        boolean forwardChat,
        boolean reportJoinQuit,
        boolean receiveChat,
        int messagePollSeconds,
        int heartbeatIntervalSeconds,
        int whitelistPollSeconds,
        int economyIntervalSeconds,
        String economyReport,
        String webRegisterUrl
) {

    public static PluginConfig load(FileConfiguration config) {
        return new PluginConfig(
                config.getString("role", "primary"),
                config.getString("language", "zh"),
                trimTrailingSlash(config.getString("backend.url", "http://127.0.0.1:18898")),
                config.getString("backend.server-id", "main"),
                config.getString("backend.server-name", ""),
                config.getString("backend.server-token", ""),
                config.getString("check.fail-policy", "cache").toLowerCase(),
                config.getInt("check.cache-ttl-seconds", 60),
                config.getInt("check.timeout-ms", 1500),
                config.getBoolean("features.enforce-whitelist", true),
                config.getBoolean("features.forward-chat", false),
                config.getBoolean("features.report-join-quit", false),
                config.getBoolean("features.receive-chat", true),
                Math.max(1, config.getInt("features.message-poll-seconds", 2)),
                Math.max(10, config.getInt("tasks.heartbeat-interval", 60)),
                Math.max(5, config.getInt("tasks.whitelist-poll-interval", 30)),
                Math.max(30, config.getInt("tasks.economy-interval", 300)),
                config.getString("economy.report", "auto").toLowerCase(),
                config.getString("web-register-url", "http://localhost:18898")
        );
    }

    /** 心跳上报的显示名：显式配置优先，缺省回退 server-id */
    public String displayName() {
        return serverName != null && !serverName.isBlank() ? serverName : serverId;
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
