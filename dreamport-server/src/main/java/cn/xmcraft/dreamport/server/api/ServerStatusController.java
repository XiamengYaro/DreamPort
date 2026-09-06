package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.stats.ServerStatsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务器状态（契约对齐旧版 /api/server/status 与 /api/server/player-history；
 * FIX(legacy)：多服在线数取各服心跳求和，不再重复计数。
 * M7：心跳快照/在线采样落库（docs/CHAT_SERVERINFO_PLAN.md），重启后 stale 兜底，history 支持 ?days=。
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
        var live = statsService.heartbeats();
        boolean stale = live.isEmpty();

        data.put("online", statsService.totalOnline());
        data.put("max", statsService.maxPlayers());
        data.put("totalOnline", statsService.totalOnline());
        data.put("totalServers", statsService.totalServers());
        data.put("version", statsService.version());
        data.put("tps", 20.0);

        List<Map<String, Object>> servers = new ArrayList<>();
        if (stale) {
            // 后端重启后、新心跳未到:读 dp_server 最后快照兜底(标记 stale)
            for (var ps : statsService.persistedServers()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("serverId", ps.serverId());
                s.put("serverName", ps.serverName());
                s.put("role", ps.role());
                s.put("onlinePlayers", ps.onlinePlayers());
                s.put("maxPlayers", ps.maxPlayers());
                s.put("lastSeen", ps.lastHeartbeat());
                s.put("stale", true);
                servers.add(s);
            }
            data.put("stale", true);
        } else {
            live.values().stream()
                    .sorted((a, b) -> a.serverId().compareTo(b.serverId()))
                    .forEach(hb -> {
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("serverId", hb.serverId());
                        s.put("serverName", hb.serverName());
                        s.put("role", hb.role());
                        s.put("onlinePlayers", hb.onlinePlayers());
                        s.put("maxPlayers", hb.maxPlayers());
                        s.put("lastSeen", hb.receivedAt());
                        s.put("players", hb.players());
                        servers.add(s);
                    });
        }
        data.put("servers", servers);
        body.put("data", data);
        return body;
    }

    /** 在线历史:?days= 默认 1(24h)上限 7;数据源 dp_online_history(M7 起落库) */
    @GetMapping("/player-history")
    public Map<String, Object> playerHistory(HttpServletRequest request) {
        int days = 1;
        try {
            String raw = request.getParameter("days");
            if (raw != null && !raw.isBlank()) {
                days = Integer.parseInt(raw);
            }
        } catch (NumberFormatException ignored) {
        }
        Map<String, Object> history = statsService.onlineHistory(days);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", history.get("list"));
        data.put("servers", history.get("servers"));
        data.put("days", Math.max(1, Math.min(days, 7)));
        data.put("currentOnline", statsService.totalOnline());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }
}
