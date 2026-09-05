package cn.xmcraft.dreamport.plugin.internal;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 后端 HTTP 客户端（P1：连通性；P5 扩展 login-check 缓存与事件上报）。
 * 阻塞调用必须由调用方放到异步线程（Bukkit/Folia 双调度）。
 */
public final class BackendClient {

    private final DreamPortPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final Gson gson = new Gson();

    public BackendClient(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    /** GET /api/health；返回响应体，失败返回 null */
    public String health() {
        return get("/api/health");
    }

    public String get(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(plugin.pluginConfig().backendUrl() + path))
                    .timeout(Duration.ofMillis(plugin.pluginConfig().timeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            plugin.getLogger().warning("后端请求失败 " + path + ": " + e.getMessage());
            return null;
        }
    }

    /** POST JSON（P5 用于 login-check/heartbeat/事件上报） */
    public String post(String path, Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(plugin.pluginConfig().backendUrl() + path))
                    .timeout(Duration.ofMillis(plugin.pluginConfig().timeoutMs()))
                    .header("Content-Type", "application/json")
                    .header(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_ID,
                            plugin.pluginConfig().serverId())
                    .header(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_TOKEN,
                            plugin.pluginConfig().serverToken())
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            plugin.getLogger().warning("后端请求失败 " + path + ": " + e.getMessage());
            return null;
        }
    }

    public Gson gson() {
        return gson;
    }
}
