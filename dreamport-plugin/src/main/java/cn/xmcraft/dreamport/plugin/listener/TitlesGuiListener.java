package cn.xmcraft.dreamport.plugin.listener;

import cn.xmcraft.dreamport.plugin.internal.TitlesService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * 称号 GUI 点击处理:点击已拥有称号 → 佩戴;点击佩戴中的称号 → 脱下。
 */
public class TitlesGuiListener implements Listener {

    private final TitlesService titlesService;

    public TitlesGuiListener(TitlesService titlesService) {
        this.titlesService = titlesService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof TitlesService.TitlesHolder titlesHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= titlesHolder.titles.size()) {
            return;
        }
        TitlesService.ActiveTitle clicked = titlesHolder.titles.get(slot);
        boolean isEquipped = clicked.code().equals(titlesHolder.activeCode);
        titlesService.equip(player, isEquipped ? "" : clicked.code());
    }
}
