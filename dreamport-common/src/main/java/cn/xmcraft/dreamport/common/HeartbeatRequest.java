package cn.xmcraft.dreamport.common;

import java.util.List;

/**
 * 服务器心跳上报（合并旧版 Bridge 的 register/status 协议）。
 *
 * @param serverId      服务器唯一标识
 * @param serverName    显示名
 * @param role          运行角色：primary / secondary / proxy
 * @param onlinePlayers 当前在线人数
 * @param maxPlayers    最大人数
 * @param version       服务端版本（Bukkit.getVersion()）
 * @param players       在线玩家名（secondary.send_player_list 开启时）
 * @param tps1m         近 1 分钟 TPS（Paper getTPS()[0]；代理无 tick 循环为 null）
 * @param tps5m         近 5 分钟 TPS
 * @param tps15m        近 15 分钟 TPS
 * @param avgTickMs     平均每 tick 耗时毫秒（Paper getAverageTickTime()）
 * @param memUsedMb     JVM 已用内存 MB
 * @param memMaxMb      JVM 最大内存 MB
 * @param cpuLoad       进程 CPU 占用 0~1（不可用为 null）
 * @param uptimeSeconds 进程运行秒数（不可用为 null）
 */
public record HeartbeatRequest(String serverId, String serverName, String role,
                               int onlinePlayers, int maxPlayers, String version,
                               List<String> players,
                               Double tps1m, Double tps5m, Double tps15m, Double avgTickMs,
                               Integer memUsedMb, Integer memMaxMb, Double cpuLoad,
                               Long uptimeSeconds) {

    /** 兼容旧调用方（不含指标）的构造器 */
    public HeartbeatRequest(String serverId, String serverName, String role,
                            int onlinePlayers, int maxPlayers, String version,
                            List<String> players) {
        this(serverId, serverName, role, onlinePlayers, maxPlayers, version, players,
                null, null, null, null, null, null, null, null);
    }
}
