package cn.xmcraft.dreamport.common;

/**
 * 进服白名单校验响应。
 *
 * @param decision    {@link Protocol#DECISION_ALLOW} 或 {@link Protocol#DECISION_DENY}
 * @param reasonKey   拒绝原因的 i18n 键（与旧版 login.* 键一致，如 login.banned_reason）
 * @param maintenance 维护模式标记（非 OP 拒绝进服）
 */
public record LoginCheckResponse(String decision, String reasonKey, boolean maintenance) {

    public static LoginCheckResponse allow() {
        return new LoginCheckResponse(Protocol.DECISION_ALLOW, null, false);
    }

    public static LoginCheckResponse deny(String reasonKey) {
        return new LoginCheckResponse(Protocol.DECISION_DENY, reasonKey, false);
    }

    public boolean allowed() {
        return Protocol.DECISION_ALLOW.equals(decision);
    }
}
