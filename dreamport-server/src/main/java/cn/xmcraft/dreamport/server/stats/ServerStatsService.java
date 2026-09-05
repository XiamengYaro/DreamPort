package cn.xmcraft.dreamport.server.stats;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多服状态聚合与在线人数历史。
 * - 心跳数据来自各服插件（primary 也上报自身）
 * - player-history：5 分钟粒度、保留 24h（288 条，对齐旧版 PlayerCountService）
 */
@Service
public class ServerStatsService {

    public record Heartbeat(String serverId, String serverName, String role,
                            int onlinePlayers, int maxPlayers, String version,
                            long receivedAt) {
    }

    /** serverId → 最近心跳 */
    private final Map<String, Heartbeat> heartbeats = new ConcurrentHashMap<>();
    private final ArrayDeque<long[]> history = new ArrayDeque<>();

    public void heartbeat(Heartbeat hb) {
        heartbeats.put(hb.serverId(), hb);
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

    /** 历史采样（在线总数），288 条上限 */
    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    public void sample() {
        history.addLast(new long[]{System.currentTimeMillis(), totalOnline()});
        while (history.size() > 288) {
            history.removeFirst();
        }
    }

    public synchronized ArrayDeque<long[]> history() {
        return history.clone();
    }
}
