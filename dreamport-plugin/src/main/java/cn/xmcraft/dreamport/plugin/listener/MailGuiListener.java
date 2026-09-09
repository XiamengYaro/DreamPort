package cn.xmcraft.dreamport.plugin.listener;

import cn.xmcraft.dreamport.plugin.internal.MailService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * 奖励邮箱 GUI 点击处理:点击对应槽位即领取该封邮件(执行奖励指令/存款并回执)。
 */
public class MailGuiListener implements Listener {

    private final MailService mailService;

    public MailGuiListener(MailService mailService) {
        this.mailService = mailService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MailService.MailHolder mailHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= mailHolder.mails.size()) {
            return;
        }
        var mail = mailHolder.mails.get(slot);
        // claim 在主线程同步判定:受理=true(清空槽位);拒绝=false(背包满/解析失败——
        // 槽位物品保留,玩家清理背包后可直接重试)
        if (mailService.claim(player, mail)) {
            event.getView().setItem(slot, null);
        }
    }

    // 修复审计 L5:拦截拖拽进 GUI
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MailService.MailHolder) {
            event.setCancelled(true);
        }
    }
}
