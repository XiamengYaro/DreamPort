package cn.xmcraft.dreamport.server.blessingskin;

import cn.xmcraft.dreamport.server.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 与 BlessingSkin 端插件（dreamport-oauth）的服务端通信。
 *
 * - fetchPlayers：按 email 拉取角色（5 分钟缓存）；插件契约 200=已关联、404=未关联
 * - provision / updatePassword / updatePlayerName / updateEmail：账号开通与同步
 *   （共享密钥 X-Dreamport-Secret；互通未启用时一律视为无操作成功，传输失败返回 ok=false）
 */
@Service
public class BlessingSkinService {

    private static final Logger log = LoggerFactory.getLogger(BlessingSkinService.class);
    private static final long CACHE_MS = 5 * 60 * 1000L;

    private final SystemSettingsService settingsService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public BlessingSkinService(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    private record CacheEntry(long at, List<Map<String, Object>> players) {}

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 同步结果:ok=成功或无操作;notLinked=皮肤站无该邮箱账号(幂等无操作);message=失败原因 */
    public record SyncResult(boolean ok, boolean notLinked, String message) {
        static SyncResult success() { return new SyncResult(true, false, null); }
        static SyncResult failure(String msg) { return new SyncResult(false, false, msg); }
        static SyncResult noLink() { return new SyncResult(true, true, null); }
    }

    private record BsResponse(int status, JsonNode body) {}

    // ---------- 角色数据(缓存) ----------

    /**
     * @return 角色列表；未关联返回 null（与空角色列表区分，前端提示文案不同）
     */
    public List<Map<String, Object>> fetchPlayers(String bsUrl, String apiSecret, String email) throws Exception {
        CacheEntry hit = cache.get(email);
        if (hit != null && System.currentTimeMillis() - hit.at() < CACHE_MS) {
            return hit.players();
        }
        String url = normalize(bsUrl) + "/dreamport/api/players?email=" + urlEncode(email);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("X-Dreamport-Secret", apiSecret)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) {
            cache.put(email, new CacheEntry(System.currentTimeMillis(), null));
            return null;
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("BlessingSkin 接口返回 HTTP " + resp.statusCode());
        }
        JsonNode data = mapper.readTree(resp.body()).path("data").path("players");
        List<Map<String, Object>> players = new java.util.ArrayList<>();
        if (data.isArray()) {
            for (JsonNode p : data) {
                players.add(mapper.convertValue(p, Map.class));
            }
        }
        cache.put(email, new CacheEntry(System.currentTimeMillis(), players));
        return players;
    }

    /** 管理端保存配置后清空缓存，避免旧域名/密钥缓存残留 */
    public void clearCache() {
        cache.clear();
    }

    // ---------- 账号开通与同步 ----------

    /** 互通未启用/配置不全 → 所有同步按无操作成功处理(不阻断用户正常账号操作) */
    private boolean bsReady() {
        var cfg = settingsService.blessingskinConfig();
        return Boolean.TRUE.equals(cfg.get("enabled"))
                && str(cfg.get("url")).isBlank() == false
                && str(cfg.get("apiSecret")).isBlank() == false;
    }

    /** 注册一键开通:建账号(密码=DreamPort 密码)+ 同名角色;幂等 */
    public SyncResult provision(String email, String playerName, String nickname, String password, String ip) {
        if (!bsReady()) return SyncResult.failure("BlessingSkin 互通未启用");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("playerName", playerName);
        payload.put("nickname", nickname == null || nickname.isBlank() ? playerName : nickname);
        payload.put("password", password);
        if (ip != null && !ip.isBlank()) payload.put("ip", ip);
        BsResponse resp = callApi("/dreamport/api/provision", payload);
        if (resp == null) return SyncResult.failure("皮肤站接口暂不可用");
        if (resp.status() == 200 && resp.body().path("success").asBoolean(false)) {
            JsonNode data = resp.body().path("data");
            return data.path("playerCreated").asBoolean(false)
                    ? SyncResult.success()
                    : SyncResult.failure(data.path("reason").asText("皮肤站角色未能创建"));
        }
        return SyncResult.failure(resp.body().path("message").asText("皮肤站返回错误"));
    }

    /** 改密同步;互通未启用或皮肤站无此账号 → 无操作成功 */
    public SyncResult updatePassword(String email, String newPassword) {
        if (email == null || email.isBlank() || !bsReady()) return SyncResult.noLink();
        BsResponse resp = callApi("/dreamport/api/update-password",
                Map.of("email", email, "password", newPassword));
        if (resp == null) return SyncResult.failure("皮肤站接口暂不可用");
        if (resp.status() == 404) return SyncResult.noLink();
        if (resp.status() == 200) return SyncResult.success();
        return SyncResult.failure(resp.body().path("message").asText("皮肤站返回错误"));
    }

    /** 角色改名同步:旧角色不存在且用户无任何角色 → 按新名新建;互通未启用/未关联 → 无操作成功 */
    public SyncResult updatePlayerName(String email, String oldName, String newName) {
        if (email == null || email.isBlank() || !bsReady()) return SyncResult.noLink();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("oldName", oldName == null ? "" : oldName);
        payload.put("newName", newName);
        BsResponse resp = callApi("/dreamport/api/update-player-name", payload);
        if (resp == null) return SyncResult.failure("皮肤站接口暂不可用");
        if (resp.status() == 404) return SyncResult.noLink();
        if (resp.status() == 200 && resp.body().path("success").asBoolean(false)) return SyncResult.success();
        return SyncResult.failure(resp.body().path("message").asText("皮肤站返回错误"));
    }

    /** 邮箱改绑(关联键):先皮肤站成功才允许本站落库;未关联/互通未启用 → 无操作成功 */
    public SyncResult updateEmail(String oldEmail, String newEmail) {
        if (oldEmail == null || oldEmail.isBlank() || !bsReady()) return SyncResult.noLink();
        BsResponse resp = callApi("/dreamport/api/update-email",
                Map.of("oldEmail", oldEmail, "newEmail", newEmail));
        if (resp == null) return SyncResult.failure("皮肤站同步失败，请稍后重试");
        if (resp.status() == 404) return SyncResult.noLink();
        if (resp.status() == 200) return SyncResult.success();
        return SyncResult.failure(resp.body().path("message").asText("皮肤站返回错误"));
    }

    // ---------- 内部工具 ----------

    /** 统一 JSON POST;互通未启用/配置不全或传输失败返回 null(调用方按需降级) */
    private BsResponse callApi(String path, Map<String, Object> payload) {
        var cfg = settingsService.blessingskinConfig();
        if (!Boolean.TRUE.equals(cfg.get("enabled"))) return null;
        String url = str(cfg.get("url"));
        String secret = str(cfg.get("apiSecret"));
        if (url.isBlank() || secret.isBlank()) return null;
        try {
            String json = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalize(url) + path))
                    .header("X-Dreamport-Secret", secret)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new BsResponse(resp.statusCode(), mapper.readTree(resp.body()));
        } catch (Exception e) {
            log.warn("[BlessingSkin] {} 调用失败: {}", path, e.getMessage());
            return null;
        }
    }

    private String normalize(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
