package cn.xmcraft.dreamport.common;

/**
 * 心跳应答。
 *
 * @param ok            是否受理
 * @param serverTime    后端时间（毫秒 epoch，供插件校时）
 */
public record HeartbeatResponse(boolean ok, long serverTime) {
}
