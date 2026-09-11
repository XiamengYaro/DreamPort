package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.security.ServerTokenVerifier;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 玩家个人位置(网页地图「我的位置」数据源):
 * - POST /internal/v1/locations(server-token):插件上报家(Essentials)+上次死亡;homes/death 缺省字段互不覆盖
 * - GET /api/user/locations(JWT):仅返回当前登录者本人数据(死亡位置属个人敏感数据,无公开/管理员通道)
 */
@RestController
public class PlayerLocationsController {

    private final JdbcTemplate jdbc;
    private final ServerTokenVerifier serverTokenVerifier;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlayerLocationsController(JdbcTemplate jdbc, ServerTokenVerifier serverTokenVerifier) {
        this.jdbc = jdbc;
        this.serverTokenVerifier = serverTokenVerifier;
    }

    public record LocationHome(String name, String world, double x, double y, double z) {
    }

    public record LocationDeath(String world, double x, double y, double z, Long diedAt) {
    }

    public record LocationsBody(String username, String uuid,
                                List<LocationHome> homes, LocationDeath death) {
    }

    @PostMapping("/internal/v1/locations")
    public ResponseEntity<Object> report(@RequestBody LocationsBody body,
                                         HttpServletRequest request) {
        if (!serverTokenVerifier.verify(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", 401, "message", "服务器认证失败"));
        }
        if (body == null || body.username() == null || body.username().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("username 必填"));
        }
        String username = body.username().trim();
        long now = System.currentTimeMillis();
        String homesJson = body.homes() == null ? null : writeJson(body.homes());
        String deathJson = body.death() == null ? null : writeJson(body.death());
        if (homesJson == null && deathJson == null) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        var rows = jdbc.queryForList("SELECT id FROM dp_player_locations WHERE username = ?", username);
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO dp_player_locations (username, uuid, homes, last_death, updated_at) VALUES (?, ?, ?, ?, ?)",
                    username, body.uuid(), homesJson, deathJson, now);
            return ResponseEntity.ok(Map.of("success", true));
        }
        // 局部更新:homes/death 缺省字段互不覆盖(重启后死亡内存清空时不误清已存死亡)
        if (homesJson != null && deathJson != null) {
            jdbc.update("UPDATE dp_player_locations SET uuid = COALESCE(?, uuid), homes = ?, last_death = ?, updated_at = ? WHERE username = ?",
                    body.uuid(), homesJson, deathJson, now, username);
        } else if (homesJson != null) {
            jdbc.update("UPDATE dp_player_locations SET uuid = COALESCE(?, uuid), homes = ?, updated_at = ? WHERE username = ?",
                    body.uuid(), homesJson, now, username);
        } else {
            jdbc.update("UPDATE dp_player_locations SET uuid = COALESCE(?, uuid), last_death = ?, updated_at = ? WHERE username = ?",
                    body.uuid(), deathJson, now, username);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/api/user/locations")
    public ResponseEntity<Object> mine(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        var rows = jdbc.queryForList(
                "SELECT homes, last_death, updated_at FROM dp_player_locations WHERE username = ?", me);
        List<Map<String, Object>> homes = new ArrayList<>();
        Map<String, Object> death = null;
        long updatedAt = 0;
        if (!rows.isEmpty()) {
            String homesJson = rows.get(0).get("homes") == null ? null : String.valueOf(rows.get(0).get("homes"));
            String deathJson = rows.get(0).get("last_death") == null ? null : String.valueOf(rows.get(0).get("last_death"));
            updatedAt = rows.get(0).get("updated_at") == null ? 0 : ((Number) rows.get(0).get("updated_at")).longValue();
            homes = readList(homesJson);
            if (deathJson != null) {
                try {
                    var node = mapper.readTree(deathJson);
                    death = Map.of(
                            "world", node.path("world").asText(""),
                            "x", node.path("x").asDouble(),
                            "y", node.path("y").asDouble(),
                            "z", node.path("z").asDouble(),
                            "diedAt", node.path("diedAt").asLong(0));
                } catch (Exception ignored) {
                }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("homes", homes);
        data.put("death", death);
        data.put("updatedAt", updatedAt);
        return ResponseEntity.ok(ApiResponse.success(null, data));
    }

    private List<Map<String, Object>> readList(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            for (JsonNode n : mapper.readTree(json)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", n.path("name").asText(""));
                item.put("world", n.path("world").asText(""));
                item.put("x", n.path("x").asDouble());
                item.put("y", n.path("y").asDouble());
                item.put("z", n.path("z").asDouble());
                result.add(item);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
