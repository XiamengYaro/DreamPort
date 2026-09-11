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
    public static final String ACTIVITY = "/internal/v1/activity";
    public static final String SIGNIN = "/internal/v1/signin";
    /** 个人位置上报(家+上次死亡,网页地图「我的位置」数据源) */
    public static final String LOCATIONS = "/internal/v1/locations";
    public static final String MAIL_PENDING = "/internal/v1/mail/pending";
    public static final String MAIL_CLAIMED = "/internal/v1/mail/claimed";
    /** 礼包采集上传(/xmw kit save,管理员服内采集背包存为礼包模板) */
    public static final String KIT_SAVE = "/internal/v1/kit/save";
    /** 礼包模板列表(/xmw kit list) */
    public static final String KIT_LIST = "/internal/v1/kit/list";
    /** 当前佩戴称号(插件 PAPI 变量 %dreamport_title% 用) */
    public static final String TITLE_ACTIVE = "/internal/v1/title/active";
    /** 玩家已拥有称号(/titles GUI 用) */
    public static final String TITLE_MINE = "/internal/v1/title/mine";
    /** 游戏内佩戴/脱下(code 为空 = 脱下) */
    public static final String TITLE_EQUIP = "/internal/v1/title/equip";

    /** 校验放行 */
    public static final String DECISION_ALLOW = "allow";
    /** 校验拒绝 */
    public static final String DECISION_DENY = "deny";

    private Protocol() {
    }
}
