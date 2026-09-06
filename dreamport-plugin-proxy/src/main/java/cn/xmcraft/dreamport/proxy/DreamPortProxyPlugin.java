package cn.xmcraft.dreamport.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * DreamPort Velocity 代理端插件：
 * - 代理端统一登录拦截（ServerPreConnectEvent 时调后端 login-check）
 * - 服务器状态心跳上报（合并旧版 Bridge）
 * - /vdp 命令（状态/重载）
 *
 * config.properties 放 plugins/dreamport-proxy/：
 *   backend.url=http://127.0.0.1:18898
 *   backend.server-id=proxy
 *   backend.server-token=xxx
 *   check.fail-policy=cache
 *   check.cache-ttl-seconds=60
 *   enforce-whitelist=true
 *   heartbeat-interval=60
 */
@Plugin(id = "dreamport-proxy", name = "DreamPort Proxy",
        version = "0.1.0", description = "DreamPort Velocity 代理端：统一白名单拦截与状态上报",
        authors = {"Xia_Meng_"})
public class DreamPortProxyPlugin {

    private static final String[] BANNER_LINES = {
            " _____                           _____           _   ",
            " |  __ \\                         |  __ \\         | |  ",
            " | |  | |_ __ ___  __ _ _ __ ___ | |__) |__  _ __| |_ ",
            " | |  | | '__/ _ \\/ _` | '_ ` _ \\|  ___/ _ \\| '__| __|",
            " | |__| | | |  __/ (_| | | | | | | |  | (_) | |  | |_ ",
            "  |_____/|_|  \\___|\\__,_|_| |_| |_|_|   \\___/|_|   \\__|"
    };

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    private String backendUrl = "http://127.0.0.1:18898";
    private String serverId = "proxy";
    private String serverToken = "";
    private String failPolicy = "cache";
    private long cacheTtlSeconds = 60;
    private boolean enforceWhitelist = true;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** username(小写) → 缓存决策 */
    private final Map<String, CacheEntry> decisionCache = new ConcurrentHashMap<>();

    private record CacheEntry(boolean allowed, String reasonKey, long cachedAt) {
    }

    @Inject
    public DreamPortProxyPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        long start = System.currentTimeMillis();
        printBanner();
        loadConfig();
        startHeartbeat();
        printStartupReport(start);
    }

    private void printBanner() {
        logger.info("");
        for (String line : BANNER_LINES) {
            logger.info(line);
        }
        logger.info("    夏日小镇 · 梦港  ——  DreamPort Velocity 代理端");
        logger.info("");
    }

    private void printStartupReport(long startMillis) {
        logger.info("┌──────────────────── 启动记录 ────────────────────┐");
        report("插件版本", "v0.1.0");
        report("后端地址", backendUrl);
        report("服务器标识", serverId);
        report("白名单拦截", enforceWhitelist ? "开启（fail-policy: " + failPolicy + "）" : "关闭");
        report("决策缓存", cacheTtlSeconds + " 秒");
        report("已注册后端服", String.valueOf(proxy.getAllServers().size()));
        proxy.getAllServers().forEach(server ->
                report("  · " + server.getServerInfo().getName(),
                        server.getPlayersConnected().size() + " 在线"));
        report("在线玩家", String.valueOf(proxy.getPlayerCount()));
        double seconds = (System.currentTimeMillis() - startMillis) / 1000.0;
        report("启动耗时", String.format("%.1f 秒", seconds));
        logger.info("└──────────────────────────────────────────────────┘");
        logger.info("");
    }

    private void report(String key, String value) {
        int pad = Math.max(1, 16 - key.length() - countCjk(key));
        logger.info("│ ✓ " + key + " ".repeat(pad) + value);
    }

    private int countCjk(String s) {
        int n = 0;
        for (char c : s.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {
                n++;
            }
        }
        return n;
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        logger.info("DreamPort Proxy 已关闭");
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDir);
            Path cfg = dataDir.resolve("config.properties");
            if (!Files.exists(cfg)) {
                try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
                    if (in != null) {
                        Files.writeString(cfg, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
                logger.info("已生成默认配置 {}", cfg);
            }
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(cfg)) {
                props.load(in);
            }
            backendUrl = trimSlash(props.getProperty("backend.url", backendUrl));
            serverId = props.getProperty("backend.server-id", serverId);
            serverToken = props.getProperty("backend.server-token", serverToken);
            failPolicy = props.getProperty("check.fail-policy", failPolicy).toLowerCase();
            cacheTtlSeconds = Long.parseLong(props.getProperty("check.cache-ttl-seconds", "60"));
            enforceWhitelist = Boolean.parseBoolean(props.getProperty("enforce-whitelist", "true"));
        } catch (IOException | NumberFormatException e) {
            logger.warn("配置加载失败（使用默认值）: {}", e.getMessage());
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 进服前拦截：后端 login-check 决策 */
    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (!enforceWhitelist) {
            return;
        }
        Player player = event.getPlayer();
        String username = player.getUsername();

        Boolean decision = checkAllowed(username, player.getRemoteAddress().getAddress().getHostAddress());
        if (decision == null || decision) {
            return; // 放行（null = 后端不可达且 fail-policy=allow）
        }
        // 拒绝：断开并提示
        player.disconnect(Component.text("你还未注册白名单，请先在官网注册\n", NamedTextColor.RED)
                .append(Component.text(backendUrl.replace("http://", "http://"), NamedTextColor.YELLOW)));
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
    }

    /** @return true=允许, false=拒绝, null=后端不可达 */
    private Boolean checkAllowed(String username, String ip) {
        String key = username.toLowerCase();
        long now = System.currentTimeMillis();
        CacheEntry cached = decisionCache.get(key);
        if (cached != null && now - cached.cachedAt() < cacheTtlSeconds * 1000L) {
            return cached.allowed();
        }
        try {
            String body = "{\"username\":\"" + escape(username) + "\",\"ip\":\"" + escape(ip) + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/internal/v1/login-check"))
                    .timeout(Duration.ofMillis(2000))
                    .header("Content-Type", "application/json")
                    .header(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_ID, serverId)
                    .header(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_TOKEN, serverToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String json = response.body();
                boolean allowed = json.contains("\"decision\":\"allow\"");
                decisionCache.put(key, new CacheEntry(allowed, null, now));
                return allowed;
            }
            logger.warn("login-check 非法响应 {} {}", response.statusCode(), response.body());
            return failDecision(cached);
        } catch (Exception e) {
            logger.warn("login-check 请求失败: {}", e.getMessage());
            return failDecision(cached);
        }
    }

    private Boolean failDecision(CacheEntry cached) {
        return switch (failPolicy) {
            case "allow" -> true;
            case "deny" -> false;
            default -> cached != null ? cached.allowed() : false;
        };
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 心跳上报 */
    private void startHeartbeat() {
        proxy.getScheduler().buildTask(this, () -> {
            try {
                int totalOnline = proxy.getPlayerCount();
                int totalMax = proxy.getConfiguration().getShowMaxPlayers();
                StringBuilder players = new StringBuilder("[");
                int i = 0;
                for (Player p : proxy.getAllPlayers()) {
                    if (i++ > 0) players.append(',');
                    players.append('"').append(p.getUsername()).append('"');
                }
                players.append(']');
                String body = "{\"serverId\":\"" + escape(serverId) + "\",\"serverName\":\"Velocity 代理\","
                        + "\"role\":\"proxy\",\"onlinePlayers\":" + totalOnline
                        + ",\"maxPlayers\":" + totalMax
                        + ",\"version\":\"Velocity\",\"players\":" + players + "}";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(backendUrl + "/internal/v1/heartbeat"))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/json")
                        .header(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_ID, serverId)
                        .header(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_TOKEN, serverToken)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                logger.debug("心跳上报失败: {}", e.getMessage());
            }
        }).repeat(60, TimeUnit.SECONDS).schedule();
    }

    /** 各后端服在线人数查询（/vdp servers 用） */
    @SuppressWarnings("unused")
    private void logServerCounts() {
        for (RegisteredServer server : proxy.getAllServers()) {
            logger.info("  {} → {} 在线", server.getServerInfo().getName(), server.getPlayersConnected().size());
        }
    }
}
