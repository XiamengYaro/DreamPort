package cn.xmcraft.dreamport.plugin.listener;

import cn.xmcraft.dreamport.common.LoginCheckResponse;
import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import cn.xmcraft.dreamport.plugin.i18n.I18nManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;

/**
 * 进服拦截（primary 角色）：
 * 1. 维护模式（后端决策）放行 OP
 * 2. 缓存优先的 login-check（fail_policy 兜底）
 * 3. 成功后异步记录登录尝试（供网页 ID 验证比对，3 分钟窗口）
 *
 * 注意：loginCheck 可能同步阻塞最长 timeout-ms（与旧版同步查库等价），
 * 缓存命中时零开销；事件在认证线程执行，不阻塞主线程。
 */
public class LoginListener implements Listener {

    private final DreamPortPlugin plugin;
    private final I18nManager i18n;

    public LoginListener(DreamPortPlugin plugin, I18nManager i18n) {
        this.plugin = plugin;
        this.i18n = i18n;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        var cfg = plugin.pluginConfig();
        if (!"primary".equals(cfg.role()) || !cfg.enforceWhitelist()) {
            return;
        }
        var player = event.getPlayer();
        String username = player.getName();
        String uuid = player.getUniqueId().toString();
        InetAddress address = event.getAddress();
        String ip = address != null ? address.getHostAddress() : "unknown";

        // 无条件记录进服尝试（供网页 ID 验证比对）——对齐旧版：先记录后校验。
        // 关键场景：玩家用绑定的 MC ID（≠网站账号名）进服完成 ID 验证，
        // 此时 login-check 会拒绝，但记录必须落库，网页验证才能成功。
        plugin.backendClient().recordLogin(username, uuid, ip);

        LoginCheckResponse decision = plugin.backendClient().loginCheck(username, uuid, ip);

        if (decision.maintenance() && !player.isOp()) {
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, i18n.msg("maintenance.kick"));
            return;
        }
        if (decision.allowed()) {
            return;
        }
        String reasonKey = decision.reasonKey() == null ? "login.unknown_status" : decision.reasonKey();
        String message = i18n.msg(reasonKey);
        if (reasonKey.equals("login.not_registered")) {
            message = message + "\n§e" + cfg.webRegisterUrl();
        }
        if (reasonKey.equals("login.banned_reason")) {
            // reasonKey 不携带原因内容（后端策略：原因走审计/查询），此处按通用封禁文案处理
            message = i18n.msg("login.banned");
        }
        event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, message);
    }
}
