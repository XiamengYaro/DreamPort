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
 * - 资源指标：心跳携带的 TPS/内存/CPU 写 **dp_server_metrics**（V11，7 天保留）
 * - 按服令牌：dp_server.token_hash（SHA-256）启用后的查询/写入/缓存（per_server 模式）
 */
@Service
public class ServerStatsService {

    private static final Logger log = LoggerFactory.getLogger(ServerStatsService.class);
    private static final long TOKEN_CACHE_TTL_MS = 30_000;

    public record Heartbeat(String serverId, String serverName, String role,
                            int onlinePlayers, int maxPlayers, String version,
                            List<String> players, long receivedAt,
                            Double tps1m, Double tps5m, Double tps15m, Double avgTickMs,
                            Integer memUsedMb, Integer memMaxMb, Double cpuLoad,
                            Long uptimeSeconds) {

        /** 兼容既有调用方（不含指标） */
        public Heartbeat(String serverId, String serverName, String role,
                         int onlinePlayers, int maxPlayers, String version,
                         List<String> players, long receivedAt) {
            this(serverId, serverName, role, onlinePlayers, maxPlayers, version, players, receivedAt,
                    null, null, null, null, null, null, null, null);
        }
    }

    /** dp_server 持久化快照（后端重启后、新心跳到达前的 stale 数据） */
    public record PersistedServer(String serverId, String serverName, String role, String version,
                                  int onlinePlayers, int maxPlayers, long lastHeartbeat) {
    }

    /** 服务器注册表管理行（后台服务器管理 Tab 用） */
    public record AdminServer(String serverId, String serverName, String role, String version,
                              int onlinePlayers, int maxPlayers, long lastHeartbeat,
                              boolean live, boolean enabled, boolean hasToken) {
    }

    /** serverId → 最近心跳 */
    private final Map<String, Heartbeat> heartbeats = new ConcurrentHashMap<>();
    private final ArrayDeque<long[]> history = new ArrayDeque<>();
    private final JdbcTemplate jdbc;

    /** 按服令牌缓存:serverId → {hash, enabled, cachedAt}（30s TTL,签发/启停时失效） */
    private record TokenEntry(String tokenHash, boolean enabled, long cachedAt) {
    }

    private final Map<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();

    public ServerStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void heartbeat(Heartbeat hb) {
        heartbeats.put(hb.serverId(), hb);
        try {
            // dp_server 为 V1 服务器注册表;token_hash 由后台签发,心跳不覆盖
            jdbc.update("INSERT INTO dp_server (server_id, name, role, version, online_players, max_players, last_heartbeat, token_hash) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, '') "
                            + "ON DUPLICATE KEY UPDATE name = VALUES(name), role = VALUES(role), version = VALUES(version), "
                            + "online_players = VALUES(online_players), max_players = VALUES(max_players), last_heartbeat = VALUES(last_heartbeat)",
                    hb.serverId(), hb.serverName(), hb.role(), hb.version(),
                    hb.onlinePlayers(), hb.maxPlayers(), hb.receivedAt());
        } catch (Exception e) {
            log.warn("服务器快照落库失败({}): {}", hb.serverId(), e.getMessage());
        }
        if (hb.tps1m() != null || hb.memUsedMb() != null || hb.cpuLoad() != null) {
            try {
                jdbc.update("INSERT INTO dp_server_metrics (server_id, tps_1m, tps_5m, tps_15m, avg_tick_ms, "
                                + "mem_used_mb, mem_max_mb, cpu_load, uptime_seconds, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        hb.serverId(), hb.tps1m(), hb.tps5m(), hb.tps15m(), hb.avgTickMs(),
                        hb.memUsedMb(), hb.memMaxMb(), hb.cpuLoad(), hb.uptimeSeconds(), hb.receivedAt());
            } catch (Exception e) {
                log.warn("资源指标落库失败({}): {}", hb.serverId(), e.getMessage());
            }
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

    /** 主服(缺省任一服)近 1 分钟 TPS;无指标数据返回 null(前端展示为未知) */
    public Double primaryTps1m() {
        return heartbeats.values().stream()
                .filter(h -> h.tps1m() != null)
                .sorted((a, b) -> {
                    boolean pa = "primary".equals(a.role());
                    boolean pb = "primary".equals(b.role());
                    return pa == pb ? a.serverId().compareTo(b.serverId()) : (pa ? -1 : 1);
                })
                .findFirst().map(Heartbeat::tps1m).orElse(null);
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
            jdbc.update("DELETE FROM dp_server_metrics WHERE created_at < ?",
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

    /**
     * 资源指标历史（dp_server_metrics）。
     *
     * @param hours 1–168(7 天)
     * @return {list: [{time, tps1m, tps5m, tps15m, avgTickMs, memUsedMb, memMaxMb, cpuLoad}]}
     */
    public List<Map<String, Object>> metricsHistory(String serverId, int hours) {
        int h = Math.max(1, Math.min(hours, 168));
        long cutoff = System.currentTimeMillis() - h * 3600_000L;
        try {
            return jdbc.queryForList(
                    "SELECT created_at AS time, tps_1m AS tps1m, tps_5m AS tps5m, tps_15m AS tps15m, "
                            + "avg_tick_ms AS avgTickMs, mem_used_mb AS memUsedMb, mem_max_mb AS memMaxMb, cpu_load AS cpuLoad "
                            + "FROM dp_server_metrics WHERE server_id = ? AND created_at >= ? ORDER BY created_at",
                    serverId, cutoff);
        } catch (Exception e) {
            log.warn("资源指标查询失败({}): {}", serverId, e.getMessage());
            return List.of();
        }
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

    /** 后台服务器管理列表:dp_server 注册表 + 实时心跳合并 */
    public List<AdminServer> adminServers() {
        Map<String, Heartbeat> live = heartbeats;
        List<AdminServer> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT server_id, name, role, version, online_players, max_players, last_heartbeat, "
                            + "enabled, token_hash FROM dp_server ORDER BY server_id");
            for (Map<String, Object> row : rows) {
                String serverId = String.valueOf(row.get("server_id"));
                Heartbeat hb = live.get(serverId);
                Object hash = row.get("token_hash");
                result.add(new AdminServer(
                        serverId,
                        hb != null ? hb.serverName() : (row.get("name") == null ? null : String.valueOf(row.get("name"))),
                        row.get("role") == null ? null : String.valueOf(row.get("role")),
                        row.get("version") == null ? null : String.valueOf(row.get("version")),
                        hb != null ? hb.onlinePlayers() : ((Number) row.get("online_players")).intValue(),
                        hb != null ? hb.maxPlayers() : ((Number) row.get("max_players")).intValue(),
                        hb != null ? hb.receivedAt() : ((Number) row.get("last_heartbeat")).longValue(),
                        hb != null,
                        row.get("enabled") == null || ((Number) row.get("enabled")).intValue() != 0,
                        hash != null && !String.valueOf(hash).isBlank()));
            }
        } catch (Exception e) {
            log.warn("服务器注册表查询失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 按服令牌校验取值（30s 缓存）。
     * @return 服务器令牌哈希;服务器不存在/未启用/未签发返回 null
     */
    public String tokenHashOf(String serverId) {
        long now = System.currentTimeMillis();
        TokenEntry entry = tokenCache.get(serverId);
        if (entry == null || now - entry.cachedAt() > TOKEN_CACHE_TTL_MS) {
            try {
                var rows = jdbc.queryForList(
                        "SELECT token_hash, enabled FROM dp_server WHERE server_id = ?", serverId);
                if (rows.isEmpty()) {
                    entry = new TokenEntry(null, false, now);
                } else {
                    String hash = String.valueOf(rows.get(0).get("token_hash"));
                    boolean enabled = rows.get(0).get("enabled") == null
                            || ((Number) rows.get(0).get("enabled")).intValue() != 0;
                    entry = new TokenEntry(hash == null || hash.isBlank() ? null : hash, enabled, now);
                }
            } catch (Exception e) {
                log.warn("按服令牌查询失败({}): {}", serverId, e.getMessage());
                entry = new TokenEntry(null, false, now);
            }
            tokenCache.put(serverId, entry);
        }
        return entry.enabled() ? entry.tokenHash() : null;
    }

    /** 签发/轮换按服令牌（写入 SHA-256 哈希,行不存在则注册） */
    public void setServerToken(String serverId, String tokenHash) {
        jdbc.update("INSERT INTO dp_server (server_id, name, role, token_hash, enabled) VALUES (?, ?, 'secondary', ?, TRUE) "
                        + "ON DUPLICATE KEY UPDATE token_hash = VALUES(token_hash)",
                serverId, serverId, tokenHash);
        tokenCache.remove(serverId);
    }

    /** 启用/停用某服(停用后该服所有 internal 请求被拒,per_server 模式) */
    public void setServerEnabled(String serverId, boolean enabled) {
        jdbc.update("UPDATE dp_server SET enabled = ? WHERE server_id = ?", enabled, serverId);
        tokenCache.remove(serverId);
    }
}
