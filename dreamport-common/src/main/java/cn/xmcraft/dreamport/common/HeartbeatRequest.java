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
 */
public record HeartbeatRequest(String serverId, String serverName, String role,
                               int onlinePlayers, int maxPlayers, String version,
                               List<String> players) {
}
