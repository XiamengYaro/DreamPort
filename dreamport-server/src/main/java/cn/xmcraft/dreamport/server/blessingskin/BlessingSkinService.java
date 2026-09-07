package cn.xmcraft.dreamport.server.blessingskin;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 与 BlessingSkin 端插件（dreamport-oauth）的服务端通信：
 * 按用户 email 拉取其在皮肤站的角色（pid/name/皮肤/披风），结果缓存 5 分钟。
 * 插件契约：GET {bs}/dreamport/api/players?email=...，请求头 X-Dreamport-Secret；
 * 200 = 已关联（players 可为空数组），404 = 该 email 在皮肤站无账号（未关联）。
 */
@Service
public class BlessingSkinService {

    private static final Logger log = LoggerFactory.getLogger(BlessingSkinService.class);
    private static final long CACHE_MS = 5 * 60 * 1000L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private record CacheEntry(long at, List<Map<String, Object>> players) {}

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

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
        List<Map<String, Object>> players = new ArrayList<>();
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

    private String normalize(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
