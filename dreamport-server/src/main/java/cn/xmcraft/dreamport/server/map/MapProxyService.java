package cn.xmcraft.dreamport.server.map;

import cn.xmcraft.dreamport.server.settings.SettingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图在线玩家代理（/api/map/live）：
 * - 地图条目只允许来自 dp_setting portal.config（请求不可传 URL，杜绝 SSRF）
 * - 按 type 调用 BlueMap / Dynmap 的玩家位置 JSON 接口并归一化为 {name, world, x, y, z}
 * - 任何失败均返回空列表（前端优雅降级隐藏侧栏,iframe 嵌入不受影响）
 */
@Service
public class MapProxyService {

    private static final Logger log = LoggerFactory.getLogger(MapProxyService.class);
    private static final int MAX_BODY_BYTES = 4_000_000;

    private final SettingService settingService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public MapProxyService(SettingService settingService) {
        this.settingService = settingService;
    }

    public record MapItem(String name, String url, String type) {
    }

    /** 归一化地图条目;兼容旧 map_url 竖线分隔格式(自动按 generic 迁移) */
    public List<MapItem> configuredMaps() {
        Map<String, Object> portal = settingService.getMap(SettingService.KEY_PORTAL);
        Object items = portal.get("map_items");
        if (items instanceof List<?> list && !list.isEmpty()) {
            List<MapItem> result = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String url = str(m.get("url"));
                    if (url.isBlank()) {
                        continue;
                    }
                    result.add(new MapItem(str(m.get("name")), url, normalizeType(str(m.get("type")))));
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        Object legacy = portal.get("map_url");
        if (legacy == null || String.valueOf(legacy).isBlank()) {
            return List.of();
        }
        return Arrays.stream(String.valueOf(legacy).split("\\|"))
                .map(String::trim)
                .filter(u -> !u.isBlank())
                .map(u -> new MapItem("地图", u, "generic"))
                .toList();
    }

    /**
     * 拉取第 index 张地图的在线玩家位置。
     *
     * @return {type, mapId(BlueMap 用于深链定位,可为 null), players: [{name, world, x, y, z}]}
     */
    public Map<String, Object> livePlayers(int index) {
        List<MapItem> maps = configuredMaps();
        Map<String, Object> result = new LinkedHashMap<>();
        if (index < 0 || index >= maps.size()) {
            result.put("type", "generic");
            result.put("mapId", null);
            result.put("players", List.of());
            return result;
        }
        MapItem map = maps.get(index);
        String type = map.type();
        result.put("type", type);
        result.put("mapId", null);
        result.put("players", List.of());
        if (!map.url().startsWith("http://") && !map.url().startsWith("https://")) {
            return result;
        }
        try {
            switch (type) {
                case "bluemap" -> blueMapLive(map.url(), result);
                case "dynmap" -> dynmapLive(map.url(), result);
                default -> {
                }
            }
        } catch (Exception e) {
            log.warn("地图在线玩家拉取失败({}): {}", type, e.getMessage());
        }
        return result;
    }

    /** BlueMap:/maps 取首个地图 id → /maps/{id}/live/players.json 取在线玩家 */
    private void blueMapLive(String base, Map<String, Object> result) throws Exception {
        JsonNode mapsNode = getJson(base + (base.endsWith("/") ? "" : "/") + "maps");
        if (mapsNode == null || !mapsNode.isArray() || mapsNode.isEmpty()) {
            return;
        }
        String mapId = mapsNode.get(0).path("id").asText(null);
        if (mapId == null) {
            return;
        }
        result.put("mapId", mapId);
        JsonNode live = getJson(base + (base.endsWith("/") ? "" : "/")
                + "maps/" + urlEncode(mapId) + "/live/players.json");
        if (live == null) {
            return;
        }
        JsonNode playersNode = live.has("players") ? live.get("players") : live;
        List<Map<String, Object>> players = new ArrayList<>();
        if (playersNode.isArray()) {
            for (JsonNode p : playersNode) {
                addBlueMapPlayer(players, p);
            }
        } else if (playersNode.isObject()) {
            playersNode.forEach(p -> addBlueMapPlayer(players, p));
        }
        result.put("players", players);
    }

    private void addBlueMapPlayer(List<Map<String, Object>> players, JsonNode p) {
        if (p.path("online").isBoolean() && !p.path("online").asBoolean()) {
            return;
        }
        JsonNode pos = p.path("position");
        if (pos.isMissingNode()) {
            return;
        }
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("name", str(p.path("name").asText(null)));
        player.put("world", str(p.path("world").asText(null)));
        player.put("x", pos.path("x").asDouble());
        player.put("y", pos.path("y").asDouble());
        player.put("z", pos.path("z").asDouble());
        players.add(player);
    }

    /** Dynmap:/up/configuration 取世界 → 每世界 standalone 更新文件聚合玩家 */
    private void dynmapLive(String base, Map<String, Object> result) throws Exception {
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        JsonNode config = getJson(b + "/up/configuration");
        if (config == null) {
            return;
        }
        JsonNode worlds = config.path("worlds");
        if (!worlds.isArray()) {
            return;
        }
        List<Map<String, Object>> players = new ArrayList<>();
        int limit = 0;
        for (JsonNode w : worlds) {
            if (limit++ >= 10) {
                break;
            }
            String world = w.path("name").asText(null);
            if (world == null) {
                continue;
            }
            JsonNode update = getJson(b + "/standalone/dynmap_" + urlEncode(world)
                    + ".json?world=" + urlEncode(world) + "&time=" + System.currentTimeMillis());
            if (update == null) {
                continue;
            }
            for (JsonNode p : update.path("players")) {
                if (!p.path("world").asText("").equals(world)) {
                    continue;
                }
                Map<String, Object> player = new LinkedHashMap<>();
                String name = p.path("name").asText(null);
                player.put("name", str(name == null || name.isBlank() ? p.path("account").asText(null) : name));
                player.put("world", world);
                player.put("x", p.path("x").asDouble());
                player.put("y", p.path("y").asDouble());
                player.put("z", p.path("z").asDouble());
                players.add(player);
            }
        }
        result.put("players", players);
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.body().length > MAX_BODY_BYTES) {
            return null;
        }
        try {
            return mapper.readTree(response.body());
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeType(String type) {
        return switch (type == null ? "" : type.toLowerCase()) {
            case "bluemap" -> "bluemap";
            case "dynmap" -> "dynmap";
            default -> "generic";
        };
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
