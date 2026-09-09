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
import java.util.List;
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
        version = "1.4.0", description = "DreamPort Velocity 代理端：统一白名单拦截与状态上报",
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

    /** username(小写) → 缓存决策;上限防内存膨胀(修复审计) */
    private static final int MAX_CACHE = 10_000;
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
        report("插件版本", "v1.0.1");
        report("后端地址", backendUrl);
        report("服务器标识", serverId);
        report("白名单拦截", enforceWhitelist ? "开启（fail-policy: " + failPolicy + "）" : "关闭");
        report("决策缓存", cacheTtlSeconds + " 秒");
        // 区分「已配置」与「在线」：只有能 ping 通的后端才算在线
        List<RegisteredServer> configured = new java.util.ArrayList<>(proxy.getAllServers());
        report("后端连通", checkBackendAlive());
        report("已配置后端服", String.valueOf(configured.size()));
        List<RegisteredServer> online = new java.util.ArrayList<>();
        configured.forEach(server -> {
            server.ping().thenAccept(ping -> {
                synchronized (online) {
                    online.add(server);
                }
            }).exceptionally(ex -> null);
        });
        // ping 是异步的：同步等待短时间收集结果
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        report("在线后端服", online.size() + " / " + configured.size());
        if (online.isEmpty()) {
            report("  · 后端服", "均未在线（或未装 DreamPort 插件）");
        } else {
            online.forEach(server ->
                    report("  · " + server.getServerInfo().getName(),
                            server.getPlayersConnected().size() + " 在线"));
        }
        report("在线玩家", String.valueOf(proxy.getPlayerCount()));
        double seconds = (System.currentTimeMillis() - startMillis) / 1000.0;
        report("启动耗时", String.format("%.1f 秒", seconds));
        logger.info("└──────────────────────────────────────────────────┘");
        logger.info("");
    }

    /** 启动时探测后端连通性 */
    private String checkBackendAlive() {
        try {
            long start = System.currentTimeMillis();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/api/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            if (resp.statusCode() == 200) {
                return "正常（" + latency + "ms）";
            }
            return "异常（HTTP " + resp.statusCode() + "）";
        } catch (Exception e) {
            return "失败（" + e.getMessage() + "）";
        }
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
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // 无条件记录进服尝试（供网页 ID 验证比对）——对齐旧版：先记录后校验。
        // 关键场景：玩家用绑定的 MC ID（≠网站账号名）进服完成 ID 验证，
        // 此时 login-check 会拒绝，但记录必须落库，网页验证才能成功。
        recordLogin(username, player.getUniqueId().toString(), ip);

        CacheEntry decision = checkAllowed(username, ip);
        if (decision == null || decision.allowed()) {
            return; // 放行（null = 后端不可达且 fail-policy=allow）
        }
        // 拒绝：断开并提示（按 reasonKey 映射友好文案）
        player.disconnect(disconnectMessage(decision.reasonKey()));
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
    }

    /** 上报进服尝试（fire-and-forget，异步线程） */
    private void recordLogin(String name, String uuid, String ip) {
        proxy.getScheduler().buildTask(this, () -> {
            try {
                String body = "{\"name\":\"" + escape(name) + "\",\"uuid\":\"" + escape(uuid)
                        + "\",\"ip\":\"" + escape(ip) + "\"}";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(backendUrl + "/internal/v1/login-record"))
                        .timeout(Duration.ofMillis(2000))
                        .header("Content-Type", "application/json")
                        .header(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_ID, serverId)
                        .header(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_TOKEN, serverToken)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                logger.warn("login-record 上报失败: {}", e.getMessage());
            }
        }).schedule();
    }

    /** reasonKey → 玩家可见踢出文案 */
    private Component disconnectMessage(String reasonKey) {
        String msg = switch (reasonKey == null ? "" : reasonKey) {
            case "login.pending" -> "你的白名单申请正在等待审核，请耐心等待";
            case "login.pending_review" -> "问卷已通过，等待管理员审核";
            case "login.invited_pending" -> "等待邀请人确认";
            case "login.pending_verify", "verify.recorded" -> "服务器已记录您的信息，请返回网页继续 ID 验证";
            case "login.rejected" -> "你的白名单申请已被拒绝，可前往网页重新答题或申诉";
            case "login.banned", "login.banned_reason" -> "你已被封禁";
            case "maintenance.kick" -> "服务器正在维护中，请耐心等待";
            default -> "你还未注册白名单，请先在官网注册";
        };
        Component text = Component.text(msg + "\n", NamedTextColor.RED);
        if (reasonKey == null || reasonKey.equals("login.not_registered") || reasonKey.isEmpty()) {
            text = text.append(Component.text(backendUrl, NamedTextColor.YELLOW));
        }
        return text;
    }

    /** @return 允许/拒绝决策（null 不会出现；后端不可达由 failDecision 决定） */
    private CacheEntry checkAllowed(String username, String ip) {
        String key = username.toLowerCase();
        long now = System.currentTimeMillis();
        CacheEntry cached = decisionCache.get(key);
        if (cached != null && now - cached.cachedAt() < cacheTtlSeconds * 1000L) {
            return cached;
        }
        try {
            String body = "{\"username\":\"" + escape(username) + "\",\"ip\":\"" + escape(ip) + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/internal/v1/login-check"))
                    .timeout(Duration.ofMillis(800))
                    .header("Content-Type", "application/json")
                    .header(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_ID, serverId)
                    .header(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_TOKEN, serverToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                // 修复审计 L6：Gson 解析响应,不再用 contains/正则猜决策
                var json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                boolean allowed = json.has("decision")
                        && "allow".equals(json.get("decision").getAsString());
                String reasonKey = json.has("reasonKey") && !json.get("reasonKey").isJsonNull()
                        ? json.get("reasonKey").getAsString() : "";
                if (decisionCache.size() >= MAX_CACHE) {
                    decisionCache.entrySet().removeIf(e -> now - e.getValue().cachedAt() > cacheTtlSeconds * 1000L);
                }
                decisionCache.put(key, new CacheEntry(allowed, reasonKey, now));
                return new CacheEntry(allowed, reasonKey, now);
            }
            logger.warn("login-check 非法响应 {} {}", response.statusCode(), response.body());
            return failDecision(cached);
        } catch (Exception e) {
            logger.warn("login-check 请求失败: {}", e.getMessage());
            return failDecision(cached);
        }
    }

    private CacheEntry failDecision(CacheEntry cached) {
        return switch (failPolicy) {
            case "allow" -> new CacheEntry(true, null, 0);
            case "deny" -> new CacheEntry(false, "error.backend_down", 0);
            default -> cached != null ? cached : new CacheEntry(false, "error.backend_down", 0);
        };
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private volatile boolean lastHeartbeatOk = true;

    /** 心跳上报（含连接状态终端记录） */
    private void startHeartbeat() {
        proxy.getScheduler().buildTask(this, () -> {
            long start = System.currentTimeMillis();
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
                HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
                long latency = System.currentTimeMillis() - start;
                boolean ok = resp.statusCode() == 200 && resp.body().contains("\"ok\":true");
                if (ok != lastHeartbeatOk) {
                    if (ok) {
                        logger.info("[连接] 后端恢复连接（{}ms）", latency);
                    } else {
                        logger.warn("[连接] 后端失联: HTTP {}（fail-policy: {}）", resp.statusCode(), failPolicy);
                    }
                    lastHeartbeatOk = ok;
                }
            } catch (Exception e) {
                if (lastHeartbeatOk) {
                    logger.warn("[连接] 后端失联: {}（fail-policy: {}）", e.getMessage(), failPolicy);
                    lastHeartbeatOk = false;
                }
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
