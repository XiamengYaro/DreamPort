package cn.xmcraft.dreamport.common;

/**
 * 进服白名单校验请求。
 *
 * @param username 玩家名（Java 版=MC ID；基岩版=前缀+ID，由插件侧剥离后传原始 ID）
 * @param uuid     玩家 UUID（含连字符）
 * @param ip       登录 IP（代理模式下为插件解析后的真实 IP）
 */
public record LoginCheckRequest(String username, String uuid, String ip) {
}
