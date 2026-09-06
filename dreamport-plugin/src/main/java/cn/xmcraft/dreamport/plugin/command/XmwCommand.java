package cn.xmcraft.dreamport.plugin.command;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/**
 * /xmw 命令：玩家侧 qq bind（QQ 验证绑定游戏内确认）+ 管理侧
 * reload/status/approve/reject/ban/unban/delete/list/info/version。
 * 经 /internal/v1/** 以服务器令牌调用后端。
 */
public final class XmwCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of("reload", "status", "approve", "reject", "ban",
            "unban", "delete", "list", "info", "qq", "version");

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
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> handleStatus(sender);
            case "reload" -> handleReload(sender);
            case "version" -> sender.sendMessage("§6[DreamPort] §f版本: §e"
                    + plugin.getDescription().getVersion());
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "approve", "reject", "ban", "unban", "delete" -> handleOp(sender, sub, args);
            case "qq" -> handleQq(sender, args);
            default -> sender.sendMessage("§6[DreamPort] §c未知子命令: " + args[0]);
        }
        return true;
    }

    private void handleStatus(CommandSender sender) {
        var cfg = plugin.pluginConfig();
        sender.sendMessage("§6[DreamPort] §f角色: §e" + cfg.role()
                + " §7| 后端: §e" + cfg.backendUrl()
                + " §7| 服务器: §e" + cfg.serverId()
                + " §7| fail-policy: §e" + cfg.failPolicy());
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            String body = plugin.backendClient().health();
            if (body != null && body.contains("\"status\"")) {
                sender.sendMessage("§6[DreamPort] §a后端连通: " + body);
            } else {
                sender.sendMessage("§6[DreamPort] §c后端不可达");
            }
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("dreamport.admin")) {
            noPermission(sender);
            return;
        }
        plugin.reloadPluginConfig();
        sender.sendMessage("§6[DreamPort] §a配置已重载");
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("dreamport.admin")) {
            noPermission(sender);
            return;
        }
        async(sender, () -> {
            String body = plugin.backendClient().adminList();
            if (body == null) {
                sender.sendMessage("§6[DreamPort] §c后端不可达");
                return;
            }
            JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
            JsonArray users = obj.getAsJsonArray("pendingUsers");
            if (users == null || users.isEmpty()) {
                sender.sendMessage("§6[DreamPort] §a当前没有待审核的玩家");
                return;
            }
            sender.sendMessage("§6[DreamPort] 待审核玩家 (" + users.size() + "):");
            for (var u : users) {
                sender.sendMessage("§e- " + u.getAsJsonObject().get("username").getAsString()
                        + " §7[" + u.getAsJsonObject().get("status").getAsString() + "]");
            }
        });
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dreamport.admin")) {
            noPermission(sender);
            return;
        }
        if (args.length < 2) {
            usage(sender, "/xmw info <玩家名>");
            return;
        }
        async(sender, () -> {
            String body = plugin.backendClient().adminInfo(args[1]);
            if (body == null) {
                sender.sendMessage("§6[DreamPort] §c后端不可达");
                return;
            }
            JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
            if (!obj.get("found").getAsBoolean()) {
                sender.sendMessage("§6[DreamPort] §c找不到玩家: " + args[1]);
                return;
            }
            sender.sendMessage("§6[DreamPort] 玩家信息:");
            sender.sendMessage("§e用户名: §f" + obj.get("username").getAsString());
            sender.sendMessage("§e邮箱: §f" + obj.get("email").getAsString());
            sender.sendMessage("§e状态: §f" + obj.get("status").getAsString());
            if (obj.has("banReason") && !obj.get("banReason").isJsonNull()) {
                sender.sendMessage("§e封禁原因: §c" + obj.get("banReason").getAsString());
            }
        });
    }

    private void handleOp(CommandSender sender, String action, String[] args) {
        if (!sender.hasPermission("dreamport.admin")) {
            noPermission(sender);
            return;
        }
        boolean needsReason = action.equals("reject") || action.equals("ban");
        if (args.length < 2 || needsReason && args.length < 3) {
            usage(sender, "/xmw " + action + " <玩家名>"
                    + (needsReason ? " <原因>" : ""));
            return;
        }
        String username = args[1];
        String reason = needsReason ? args[2] : "";
        async(sender, () -> {
            String body = plugin.backendClient().adminOp(action, username, reason);
            if (body == null) {
                sender.sendMessage("§6[DreamPort] §c后端不可达");
                return;
            }
            JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
            sender.sendMessage("§6[DreamPort] " + (obj.get("ok").getAsBoolean()
                    ? "§a" + obj.get("message").getAsString()
                    : "§c" + obj.get("message").getAsString()));
        });
    }

    private void async(CommandSender sender, Runnable runnable) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    /**
     * QQ 验证绑定游戏内确认（docs/ASTRBOT_PLAN.md §5.2）：
     * /xmw qq bind <验证码> —— 验证码经 QQ 机器人 /dp绑定 获取，网页或游戏内任一通道确认。
     */
    private void handleQq(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§6[DreamPort] §c只有玩家可以绑定 QQ");
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("bind")) {
            usage(sender, "/xmw qq bind <验证码>");
            return;
        }
        String code = args[2];
        async(sender, () -> {
            String body = plugin.backendClient().post("/internal/v1/qq/bind",
                    java.util.Map.of("player", player.getName(), "code", code));
            if (body == null) {
                sender.sendMessage("§6[DreamPort] §c后端不可达");
                return;
            }
            JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
            boolean ok = obj.has("success") && obj.get("success").getAsBoolean();
            String msg = obj.has("message") && !obj.get("message").isJsonNull()
                    ? obj.get("message").getAsString() : body;
            sender.sendMessage("§6[DreamPort] " + (ok ? "§a" + msg : "§c" + msg));
        });
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage("§6[DreamPort] §c没有权限");
    }

    private void usage(CommandSender sender, String usage) {
        sender.sendMessage("§6[DreamPort] §f用法: " + usage);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUB.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && "qq".equalsIgnoreCase(args[0])) {
            return List.of("bind");
        }
        return List.of();
    }
}
