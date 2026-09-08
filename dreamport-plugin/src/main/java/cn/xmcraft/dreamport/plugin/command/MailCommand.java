package cn.xmcraft.dreamport.plugin.command;

import cn.xmcraft.dreamport.plugin.internal.MailService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /mail 命令:打开奖励邮箱 GUI(仅玩家)。 */
public final class MailCommand implements CommandExecutor {

    private final MailService mailService;

    public MailCommand(MailService mailService) {
        this.mailService = mailService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§6[DreamPort] §c只有玩家可以打开奖励邮箱");
            return true;
        }
        mailService.openGui(player);
        return true;
    }
}
