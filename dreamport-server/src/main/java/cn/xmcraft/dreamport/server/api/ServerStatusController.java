package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.stats.ServerStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务器状态（契约对齐旧版 /api/server/status 与 /api/server/player-history；
 * FIX(legacy)：多服在线数取各服心跳求和，不再重复计数）。
 */
@RestController
@RequestMapping("/api/server")
public class ServerStatusController {

    private final ServerStatsService statsService;

    public ServerStatusController(ServerStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("online", statsService.totalOnline());
        data.put("max", statsService.maxPlayers());
        data.put("totalOnline", statsService.totalOnline());
        data.put("totalServers", statsService.totalServers());
        data.put("version", statsService.version());
        data.put("tps", 20.0);
        List<Map<String, Object>> servers = new ArrayList<>();
        statsService.heartbeats().values().stream()
                .sorted((a, b) -> a.serverId().compareTo(b.serverId()))
                .forEach(hb -> {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("serverId", hb.serverId());
                    s.put("serverName", hb.serverName());
                    s.put("role", hb.role());
                    s.put("onlinePlayers", hb.onlinePlayers());
                    s.put("maxPlayers", hb.maxPlayers());
                    s.put("lastSeen", hb.receivedAt());
                    servers.add(s);
                });
        data.put("servers", servers);
        body.put("data", data);
        return body;
    }

    @GetMapping("/player-history")
    public Map<String, Object> playerHistory() {
        List<Map<String, Object>> list = new ArrayList<>();
        statsService.history().forEach(point -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", point[0]);
            p.put("players", point[1]);
            list.add(p);
        });
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("currentOnline", statsService.totalOnline());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }
}
