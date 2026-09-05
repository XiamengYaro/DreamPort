package cn.xmcraft.dreamport.plugin.command;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * /xmw 命令（P1：status/reload；P5 增加 approve/reject/ban/unban/list/info/delete，对齐旧版+参考项目 吸收项 5-9）。
 */
public final class XmwCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of("status", "reload");

    private final DreamPortPlugin plugin;

    public XmwCommand(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6[DreamPort] §f用法: /xmw <" + String.join("|", SUB) + ">");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "status" -> handleStatus(sender);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage("§6[DreamPort] §c未知子命令: " + args[0]);
        }
        return true;
    }

    private void handleStatus(CommandSender sender) {
        var cfg = plugin.pluginConfig();
        sender.sendMessage("§6[DreamPort] §f角色: §e" + cfg.role()
                + " §7| 后端: §e" + cfg.backendUrl()
                + " §7| 服务器: §e" + cfg.serverId());
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            String body = plugin.backendClient().health();
            if (body != null && (body.contains("\"status\"") || body.contains("\"ok\""))) {
                sender.sendMessage("§6[DreamPort] §a后端连通: " + body);
            } else {
                sender.sendMessage("§6[DreamPort] §c后端不可达（fail-policy=" + cfg.failPolicy() + "）");
            }
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("dreamport.admin")) {
            sender.sendMessage("§6[DreamPort] §c没有权限");
            return;
        }
        plugin.reloadPluginConfig();
        sender.sendMessage("§6[DreamPort] §a配置已重载");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUB.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
