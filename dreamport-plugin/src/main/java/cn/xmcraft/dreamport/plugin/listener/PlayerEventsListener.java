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
        // 个人位置(家+死亡)上报:网页地图「我的位置」数据源
        plugin.locationsReporter().reportAsync(event.getPlayer().getName(), event.getPlayer().getUniqueId());
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
        plugin.locationsReporter().reportAsync(player.getName(), player.getUniqueId());
        if (plugin.pluginConfig().reportJoinQuit()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin,
                    task -> plugin.backendClient().sendEvent("quit", player.getName(), ""));
        }
    }

    /** 死亡位置记录(网页「上次死亡」数据源):MONITOR 记录后立即异步上报 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        var player = event.getEntity();
        var loc = player.getLocation();
        plugin.locationsReporter().onDeath(player.getName(), player.getUniqueId(),
                new cn.xmcraft.dreamport.plugin.internal.LocationReporter.DeathSpot(
                        loc.getWorld() == null ? "world" : loc.getWorld().getName(),
                        loc.getX(), loc.getY(), loc.getZ(), System.currentTimeMillis()));
    }
}
