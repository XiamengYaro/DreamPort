package cn.xmcraft.dreamport.plugin.command;

import cn.xmcraft.dreamport.plugin.internal.TitlesService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /titles —— 打开称号 GUI(佩戴/脱下) */
public final class TitlesCommand implements CommandExecutor {

    private final TitlesService titlesService;

    public TitlesCommand(TitlesService titlesService) {
        this.titlesService = titlesService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令仅玩家可用");
            return true;
        }
        titlesService.openGui(player);
        return true;
    }
}
