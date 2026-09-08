package cn.xmcraft.dreamport.plugin.listener;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import cn.xmcraft.dreamport.plugin.internal.MailService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.TimeUnit;

/**
 * 进服延迟提醒未领取的奖励邮件(拉取在异步线程,提示在主线程)。
 */
public class PlayerJoinMailNotifier implements Listener {

    private final DreamPortPlugin plugin;
    private final MailService mailService;

    public PlayerJoinMailNotifier(DreamPortPlugin plugin, MailService mailService) {
        this.plugin = plugin;
        this.mailService = mailService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        // 延迟 5 秒(100 tick),避开进服瞬间刷屏
        plugin.getServer().getAsyncScheduler().runDelayed(plugin,
                t -> mailService.notifyOnJoin(player), 100, TimeUnit.SECONDS);
    }
}
