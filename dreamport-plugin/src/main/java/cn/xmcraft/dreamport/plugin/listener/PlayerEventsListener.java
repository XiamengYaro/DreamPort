package cn.xmcraft.dreamport.plugin.listener;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家事件上报（join/quit/chat → 后端广播到网页聊天室）。
 * AsyncPlayerChatEvent 本身异步，可直接 HTTP；join/quit 用异步调度。
 */
public class PlayerEventsListener implements Listener {

    private final DreamPortPlugin plugin;
    /** 进服时间戳(会话时长统计,驱动积分任务) */
    private final java.util.Map<java.util.UUID, Long> joinAt = new java.util.concurrent.ConcurrentHashMap<>();

    public PlayerEventsListener(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.pluginConfig().forwardChat()) {
            plugin.backendClient().sendEvent("chat", event.getPlayer().getName(), event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        joinAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        plugin.titlesService().applyOnJoin(event.getPlayer());
        if (plugin.pluginConfig().reportJoinQuit()) {
            String name = event.getPlayer().getName();
            plugin.getServer().getAsyncScheduler().runNow(plugin,
                    task -> plugin.backendClient().sendEvent("join", name, ""));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        Long start = joinAt.remove(player.getUniqueId());
        // 会话时长上报(积分任务数据源,与 join/quit 聊天广播解耦)
        long seconds = start == null ? 0 : Math.max(0, (System.currentTimeMillis() - start) / 1000L);
        plugin.backendClient().reportActivity(player.getName(), seconds, 1);
        if (plugin.pluginConfig().reportJoinQuit()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin,
                    task -> plugin.backendClient().sendEvent("quit", player.getName(), ""));
        }
    }
}
