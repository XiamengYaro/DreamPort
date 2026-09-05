package cn.xmcraft.dreamport.plugin.internal;

import cn.xmcraft.dreamport.common.LoginCheckRequest;
import cn.xmcraft.dreamport.common.LoginCheckResponse;
import cn.xmcraft.dreamport.common.Protocol;
import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 后端 HTTP 客户端：
 * - loginCheck：本地缓存（TTL 可配）+ fail_policy 兜底（cache/allow/deny），阻塞调用须在异步线程
 * - 心跳/事件/登录记录上报
 */
public final class BackendClient {

    private record CacheEntry(LoginCheckResponse decision, long cachedAt) {
    }

    private final DreamPortPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final Gson gson = new Gson();
    private final Map<String, CacheEntry> decisionCache = new ConcurrentHashMap<>();

    public BackendClient(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    public String health() {
        return get("/api/health");
    }

    public String get(String path) {
        return exchange(builder -> builder.uri(URI.create(url(path)))
                .timeout(Duration.ofMillis(plugin.pluginConfig().timeoutMs()))
                .GET(), null);
    }

    public String post(String path, Object body) {
        return exchange(builder -> builder.uri(URI.create(url(path)))
                .timeout(Duration.ofMillis(plugin.pluginConfig().timeoutMs()))
                .header("Content-Type", "application/json")
                .header(Protocol.HEADER_SERVER_ID, plugin.pluginConfig().serverId())
                .header(Protocol.HEADER_SERVER_TOKEN, plugin.pluginConfig().serverToken())
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))), null);
    }

    private String url(String path) {
        return plugin.pluginConfig().backendUrl() + path;
    }

    private String exchange(java.util.function.Function<HttpRequest.Builder, HttpRequest.Builder> fn,
                            String unused) {
        try {
            HttpRequest request = fn.apply(HttpRequest.newBuilder()).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            plugin.getLogger().warning("后端请求失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 进服校验：缓存命中即返回；未命中同步调用（须在异步线程）；
     * 后端不可达时按 fail-policy：cache（含过期缓存）/ allow / deny。
     */
    public LoginCheckResponse loginCheck(String username, String uuid, String ip) {
        var cfg = plugin.pluginConfig();
        String key = username.toLowerCase();
        long now = System.currentTimeMillis();
        CacheEntry entry = decisionCache.get(key);
        if (entry != null && now - entry.cachedAt() < cfg.cacheTtlSeconds() * 1000L) {
            return entry.decision();
        }
        String body = post(Protocol.LOGIN_CHECK, new LoginCheckRequest(username, uuid, ip));
        if (body != null) {
            try {
                LoginCheckResponse decision = gson.fromJson(body, LoginCheckResponse.class);
                if (decision != null && decision.decision() != null) {
                    decisionCache.put(key, new CacheEntry(decision, now));
                    return decision;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("login-check 响应解析失败: " + e.getMessage());
            }
        }
        // 后端不可达：fail_policy
        return switch (cfg.failPolicy()) {
            case "allow" -> LoginCheckResponse.allow();
            case "deny" -> LoginCheckResponse.deny("error.backend_down");
            default -> entry != null
                    ? entry.decision()                              // cache：用过期缓存兜底
                    : LoginCheckResponse.deny("error.backend_down");
        };
    }

    public void recordLogin(String name, String uuid, String ip) {
        postAsync(Protocol.LOGIN_RECORD, Map.of("name", name, "uuid", uuid, "ip", ip));
    }

    public void sendEvent(String type, String player, String message) {
        postAsync(Protocol.EVENTS, Map.of("type", type, "serverId", plugin.pluginConfig().serverId(),
                "player", player == null ? "" : player, "message", message == null ? "" : message));
    }

    public void heartbeat(int online, int max, String version, java.util.List<String> players) {
        var cfg = plugin.pluginConfig();
        postAsync(Protocol.HEARTBEAT, new cn.xmcraft.dreamport.common.HeartbeatRequest(
                cfg.serverId(), cfg.serverId(), cfg.role(), online, max, version, players));
    }

    public void economySnapshot(String json) {
        postAsync(Protocol.ECONOMY_SNAPSHOT, gson.fromJson(json, Map.class));
    }

    public String whitelistCommands() {
        return get(Protocol.COMMANDS_WHITELIST + "?serverId=" + plugin.pluginConfig().serverId());
    }

    public String adminOp(String action, String username, String reason) {
        return post("/internal/v1/admin-ops/" + action,
                Map.of("username", username, "reason", reason == null ? "" : reason));
    }

    public String adminList() {
        return get("/internal/v1/admin-ops/list");
    }

    public String adminInfo(String username) {
        return get("/internal/v1/admin-ops/info/" + username);
    }

    private void postAsync(String path, Object body) {
        plugin.getServer().getAsyncScheduler().runNow(plugin,
                task -> post(path, body));
    }

    public Gson gson() {
        return gson;
    }
}
