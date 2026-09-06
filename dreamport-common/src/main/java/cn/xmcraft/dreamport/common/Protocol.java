package cn.xmcraft.dreamport.common;

/**
 * 插件 ↔ 后端 内部协议常量（Rules.md §5：三通道鉴权，服务器间通道走此处定义的路径与请求头）。
 */
public final class Protocol {

    /** 服务器身份请求头 */
    public static final String HEADER_SERVER_ID = "X-Server-Id";
    /** 服务器令牌请求头 */
    public static final String HEADER_SERVER_TOKEN = "X-Server-Token";

    /** 进服白名单校验 */
    public static final String LOGIN_CHECK = "/internal/v1/login-check";
    /** 进服登录尝试记录（供网页 ID 验证比对） */
    public static final String LOGIN_RECORD = "/internal/v1/login-record";
    /** 心跳：在线人数/玩家列表上报（合并旧版 Bridge 协议） */
    public static final String HEARTBEAT = "/internal/v1/heartbeat";
    /** 事件上报（join/quit/chat） */
    public static final String EVENTS = "/internal/v1/events";
    /** 经济数据快照（Vault/Essentials 采集） */
    public static final String ECONOMY_SNAPSHOT = "/internal/v1/economy/snapshot";
    /** whitelist 指令队列拉取（bukkit 模式） */
    public static final String COMMANDS_WHITELIST = "/internal/v1/commands/whitelist";
    /** 游戏收件箱轮询（网页/QQ 消息下行进服，docs/ASTRBOT_PLAN.md §5.3） */
    public static final String MESSAGES_PENDING = "/internal/v1/messages/pending";
    /** QQ 绑定游戏内确认通道（/xmw qq bind，docs/ASTRBOT_PLAN.md §5.2） */
    public static final String QQ_BIND = "/internal/v1/qq/bind";

    /** 校验放行 */
    public static final String DECISION_ALLOW = "allow";
    /** 校验拒绝 */
    public static final String DECISION_DENY = "deny";

    private Protocol() {
    }
}
