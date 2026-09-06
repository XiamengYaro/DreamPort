package cn.xmcraft.dreamport.server.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多服状态聚合与在线人数历史（docs/CHAT_SERVERINFO_PLAN.md §2.2）：
 * - 心跳数据来自各服插件（primary 也上报自身），内存聚合 + **实时 upsert dp_server**
 * - 在线采样：每 5 分钟一条写入 **dp_online_history**（7 天保留，API 查询以库为准），
 *   内存 ArrayDeque 保留（288 条 = 24h，兼容快速读取）
 */
@Service
public class ServerStatsService {

    private static final Logger log = LoggerFactory.getLogger(ServerStatsService.class);

    public record Heartbeat(String serverId, String serverName, String role,
                            int onlinePlayers, int maxPlayers, String version,
                            List<String> players, long receivedAt) {
    }

    /** dp_server 持久化快照（后端重启后、新心跳到达前的 stale 数据） */
    public record PersistedServer(String serverId, String serverName, String role, String version,
                                  int onlinePlayers, int maxPlayers, long lastHeartbeat) {
    }

    /** serverId → 最近心跳 */
    private final Map<String, Heartbeat> heartbeats = new ConcurrentHashMap<>();
    private final ArrayDeque<long[]> history = new ArrayDeque<>();
    private final JdbcTemplate jdbc;

    public ServerStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void heartbeat(Heartbeat hb) {
        heartbeats.put(hb.serverId(), hb);
        try {
            // dp_server 为 V1 服务器注册表（token_hash 暂未启用，占位空串；enabled 默认 1）
            jdbc.update("INSERT INTO dp_server (server_id, name, role, version, online_players, max_players, last_heartbeat, token_hash) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, '') "
                            + "ON DUPLICATE KEY UPDATE name = VALUES(name), role = VALUES(role), version = VALUES(version), "
                            + "online_players = VALUES(online_players), max_players = VALUES(max_players), last_heartbeat = VALUES(last_heartbeat)",
                    hb.serverId(), hb.serverName(), hb.role(), hb.version(),
                    hb.onlinePlayers(), hb.maxPlayers(), hb.receivedAt());
        } catch (Exception e) {
            log.warn("服务器快照落库失败({}): {}", hb.serverId(), e.getMessage());
        }
    }

    public Map<String, Heartbeat> heartbeats() {
        return Map.copyOf(heartbeats);
    }

    /** 全群组在线总数 */
    public int totalOnline() {
        return heartbeats.values().stream().mapToInt(Heartbeat::onlinePlayers).sum();
    }

    public int totalServers() {
        return heartbeats.size();
    }

    public int maxPlayers() {
        return heartbeats.values().stream().mapToInt(Heartbeat::maxPlayers).sum();
    }

    private String primaryVersion() {
        return heartbeats.values().stream()
                .filter(h -> "primary".equals(h.role()))
                .findFirst().map(Heartbeat::version).orElse(null);
    }

    public String version() {
        return primaryVersion();
    }

    /** 历史采样（在线总数内存 24h + 分服写 dp_online_history，7 天保留） */
    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    public void sample() {
        long now = System.currentTimeMillis();
        history.addLast(new long[]{now, totalOnline()});
        while (history.size() > 288) {
            history.removeFirst();
        }
        try {
            for (Heartbeat hb : heartbeats.values()) {
                jdbc.update("INSERT INTO dp_online_history (server_id, online, max_players, sampled_at) VALUES (?, ?, ?, ?)",
                        hb.serverId(), hb.onlinePlayers(), hb.maxPlayers(), now);
            }
            jdbc.update("DELETE FROM dp_online_history WHERE sampled_at < ?",
                    now - 7L * 24 * 3600_000);
        } catch (Exception e) {
            log.warn("在线采样落库失败: {}", e.getMessage());
        }
    }

    public synchronized ArrayDeque<long[]> history() {
        return history.clone();
    }

    /**
     * 近 N 天在线历史（1–7 天，查询 dp_online_history）。
     *
     * @return {list: [{time, players}](全群组按采样时间求和), servers: [{serverId, points: [{time, players}]}]}
     */
    public Map<String, Object> onlineHistory(int days) {
        int d = Math.max(1, Math.min(days, 7));
        long cutoff = System.currentTimeMillis() - d * 24L * 3600_000L;
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> list = jdbc.queryForList(
                    "SELECT sampled_at AS time, SUM(online) AS players FROM dp_online_history "
                            + "WHERE sampled_at >= ? GROUP BY sampled_at ORDER BY sampled_at", cutoff);
            result.put("list", list);

            Map<String, List<Map<String, Object>>> byServer = new LinkedHashMap<>();
            for (Map<String, Object> row : jdbc.queryForList(
                    "SELECT server_id, sampled_at AS time, online AS players FROM dp_online_history "
                            + "WHERE sampled_at >= ? ORDER BY sampled_at", cutoff)) {
                byServer.computeIfAbsent(String.valueOf(row.get("server_id")),
                        k -> new ArrayList<>()).add(Map.of("time", row.get("time"), "players", row.get("players")));
            }
            List<Map<String, Object>> servers = new ArrayList<>();
            byServer.forEach((serverId, points) -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("serverId", serverId);
                s.put("points", points);
                servers.add(s);
            });
            result.put("servers", servers);
        } catch (Exception e) {
            log.warn("在线历史查询失败: {}", e.getMessage());
            result.put("list", List.of());
            result.put("servers", List.of());
        }
        return result;
    }

    /** dp_server 中的最后快照（心跳未到时兜底展示，调用方标记 stale） */
    public List<PersistedServer> persistedServers() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT server_id, name, role, version, online_players, max_players, last_heartbeat "
                            + "FROM dp_server ORDER BY server_id");
            List<PersistedServer> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                result.add(new PersistedServer(
                        String.valueOf(row.get("server_id")),
                        row.get("name") == null ? null : String.valueOf(row.get("name")),
                        row.get("role") == null ? null : String.valueOf(row.get("role")),
                        row.get("version") == null ? null : String.valueOf(row.get("version")),
                        ((Number) row.get("online_players")).intValue(),
                        ((Number) row.get("max_players")).intValue(),
                        ((Number) row.get("last_heartbeat")).longValue()));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}
