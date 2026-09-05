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
        if (plugin.pluginConfig().reportJoinQuit()) {
            String name = event.getPlayer().getName();
            plugin.getServer().getAsyncScheduler().runNow(plugin,
                    task -> plugin.backendClient().sendEvent("join", name, ""));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.pluginConfig().reportJoinQuit()) {
            String name = event.getPlayer().getName();
            plugin.getServer().getAsyncScheduler().runNow(plugin,
                    task -> plugin.backendClient().sendEvent("quit", name, ""));
        }
    }
}
