package cn.xmcraft.dreamport.plugin.internal;

import cn.xmcraft.dreamport.common.LoginCheckRequest;
import cn.xmcraft.dreamport.common.LoginCheckResponse;
import cn.xmcraft.dreamport.common.PendingMessagesResponse;
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
    /** 进服决策缓存;上限 1 万条,超限时清理过期条目(修复审计:原只增不减,长期运行内存膨胀) */
    private static final int MAX_CACHE = 10_000;
    private final Map<String, CacheEntry> decisionCache = new ConcurrentHashMap<>();

    public BackendClient(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    /** 关闭底层 HTTP 客户端(热重载/插件卸载时释放连接) */
    public void close() {
        http.close();
    }

    public String health() {
        return get("/api/health");
    }

    public String get(String path) {
        return exchange(builder -> builder.uri(URI.create(url(path)))
                .timeout(Duration.ofMillis(plugin.pluginConfig().timeoutMs()))
                .GET(), null);
    }

    /** 携带服务器身份的 GET（/internal/v1/** 端点要求 X-Server-Id/X-Server-Token） */
    public String getInternal(String path) {
        return exchange(builder -> builder.uri(URI.create(url(path)))
                .timeout(Duration.ofMillis(plugin.pluginConfig().timeoutMs()))
                .header(Protocol.HEADER_SERVER_ID, plugin.pluginConfig().serverId())
                .header(Protocol.HEADER_SERVER_TOKEN, plugin.pluginConfig().serverToken())
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
                    if (decisionCache.size() >= MAX_CACHE) {
                        long ttl = cfg.cacheTtlSeconds() * 1000L;
                        decisionCache.entrySet().removeIf(e -> now - e.getValue().cachedAt() > ttl);
                    }
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

    /** 上次心跳是否成功（连接状态追踪） */
    private volatile boolean lastHeartbeatOk;
    private volatile long lastHeartbeatAt;
    private volatile long lastLatencyMs;

    public boolean lastHeartbeatOk() {
        return lastHeartbeatOk;
    }

    public long lastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public long lastLatencyMs() {
        return lastLatencyMs;
    }

    /** 连接状态变化时输出提示 */
    private void reportConnection(boolean ok, String detail) {
        boolean changed = lastHeartbeatOk != ok;
        lastHeartbeatOk = ok;
        lastHeartbeatAt = System.currentTimeMillis();
        if (changed || ok) {
            if (ok) {
                plugin.getLogger().info("[连接] 后端正常 " + detail);
            } else if (changed) {
                plugin.getLogger().warning("[连接] 后端失联: " + detail + "（fail-policy: "
                        + plugin.pluginConfig().failPolicy() + "）");
            }
        }
    }

    public void heartbeat(int online, int max, String version, java.util.List<String> players) {
        var cfg = plugin.pluginConfig();
        long start = System.currentTimeMillis();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                // 修复审计：显示名用显式 server-name(缺省回退 server-id),不再把 serverId 当名字上报
                String body = post(Protocol.HEARTBEAT, new cn.xmcraft.dreamport.common.HeartbeatRequest(
                        cfg.serverId(), cfg.displayName(), cfg.role(), online, max, version, players));
                long latency = System.currentTimeMillis() - start;
                lastLatencyMs = latency;
                reportConnection(body != null && body.contains("\"ok\":true"),
                        "（" + latency + "ms）");
            } catch (Exception e) {
                reportConnection(false, e.getMessage());
            }
        });
    }

    public void economySnapshot(String json) {
        postAsync(Protocol.ECONOMY_SNAPSHOT, gson.fromJson(json, Map.class));
    }

    public String whitelistCommands() {
        return getInternal(Protocol.COMMANDS_WHITELIST + "?serverId=" + plugin.pluginConfig().serverId());
    }

    /** 游戏收件箱增量轮询（网页/QQ 消息下行进服）；后端不可达返回 null */
    public PendingMessagesResponse pollPendingMessages(long since) {
        String body = getInternal(Protocol.MESSAGES_PENDING + "?since=" + since);
        if (body == null) {
            return null;
        }
        try {
            return gson.fromJson(body, PendingMessagesResponse.class);
        } catch (Exception e) {
            plugin.getLogger().warning("messages/pending 响应解析失败: " + e.getMessage());
            return null;
        }
    }

    /** QQ 绑定游戏内确认（/xmw qq bind），@return 后端原始响应体 */
    public String qqBind(String player, String code) {
        return post(Protocol.QQ_BIND, Map.of("player", player, "code", code));
    }

    public String adminOp(String action, String username, String reason) {
        return post("/internal/v1/admin-ops/" + action,
                Map.of("username", username, "reason", reason == null ? "" : reason));
    }

    public String adminList() {
        return getInternal("/internal/v1/admin-ops/list");
    }

    public String adminInfo(String username) {
        return getInternal("/internal/v1/admin-ops/info/" + username);
    }

    /** 玩家退出时上报会话时长(驱动每日/每周在线任务) */
    public void reportActivity(String username, long sessionSeconds, int loginCount) {
        postAsync(Protocol.ACTIVITY, java.util.Map.of("username", username,
                "sessionSeconds", sessionSeconds, "loginCount", loginCount));
    }

    /** 游戏内签到;@return 后端原始响应体(含 success/first/message) */
    public String gameSignin(String player) {
        return post(Protocol.SIGNIN, java.util.Map.of("username", player));
    }

    /** 待领取奖励邮件(原始 JSON 响应体) */
    public String mailPending(String player) {
        return getInternal(Protocol.MAIL_PENDING + "?username=" + java.net.URLEncoder.encode(player,
                java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 邮件领取回执 */
    public void mailClaimed(long id) {
        postAsync(Protocol.MAIL_CLAIMED, java.util.Map.of("id", id));
    }

    private void postAsync(String path, Object body) {
        plugin.getServer().getAsyncScheduler().runNow(plugin,
                task -> post(path, body));
    }

    public Gson gson() {
        return gson;
    }
}
