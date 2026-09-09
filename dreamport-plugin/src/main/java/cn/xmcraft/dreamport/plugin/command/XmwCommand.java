package cn.xmcraft.dreamport.plugin.command;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /xmw 命令：玩家侧 qq bind（QQ 验证绑定游戏内确认）+ 管理侧
 * reload/status/approve/reject/ban/unban/delete/list/info/version。
 * 经 /internal/v1/** 以服务器令牌调用后端。
 */
public final class XmwCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of("reload", "status", "signin", "approve", "reject",
            "ban", "unban", "delete", "list", "info", "qq", "version", "kit");

    private final DreamPortPlugin plugin;

    public XmwCommand(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            reply(sender, "§6[DreamPort] §f用法: /xmw <" + String.join("|", SUB) + ">");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> handleStatus(sender);
            case "reload" -> handleReload(sender);
            case "version" -> reply(sender, "§6[DreamPort] §f版本: §e"
                    + plugin.getDescription().getVersion());
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "approve", "reject", "ban", "unban", "delete" -> handleOp(sender, sub, args);
            case "qq" -> handleQq(sender, args);
            case "signin" -> handleSignin(sender);
            case "kit" -> handleKit(sender, args);
            default -> reply(sender, "§6[DreamPort] §c未知子命令: " + args[0]);
        }
        return true;
    }

    private void handleStatus(CommandSender sender) {
        var cfg = plugin.pluginConfig();
        reply(sender, "§6[DreamPort] §f角色: §e" + cfg.role()
                + " §7| 后端: §e" + cfg.backendUrl()
                + " §7| 服务器: §e" + cfg.serverId()
                + " §7| fail-policy: §e" + cfg.failPolicy());
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            String body = plugin.backendClient().health();
            if (body != null && body.contains("\"status\"")) {
                reply(sender, "§6[DreamPort] §a后端连通: " + body);
            } else {
                reply(sender, "§6[DreamPort] §c后端不可达");
            }
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("dreamport.admin")) {
            noPermission(sender);
            return;
        }
        plugin.reloadPluginConfig();
        reply(sender, "§6[DreamPort] §a配置已重载");
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("dreamport.admin")) {
            noPermission(sender);
            return;
        }
        async(sender, () -> {
            String body = plugin.backendClient().adminList();
            if (body == null) {
                reply(sender, "§6[DreamPort] §c后端不可达");
                return;
            }
            JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
            JsonArray users = obj.getAsJsonArray("pendingUsers");
            if (users == null || users.isEmpty()) {
                reply(sender, "§6[DreamPort] §a当前没有待审核的玩家");
                return;
            }
            reply(sender, "§6[DreamPort] 待审核玩家 (" + users.size() + "):");
            for (var u : users) {
                reply(sender, "§e- " + u.getAsJsonObject().get("username").getAsString()
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
                reply(sender, "§6[DreamPort] §c后端不可达");
                return;
            }
            JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
            if (!obj.get("found").getAsBoolean()) {
                reply(sender, "§6[DreamPort] §c找不到玩家: " + args[1]);
                return;
            }
            reply(sender, "§6[DreamPort] 玩家信息:");
            reply(sender, "§e用户名: §f" + obj.get("username").getAsString());
            reply(sender, "§e邮箱: §f" + obj.get("email").getAsString());
            reply(sender, "§e状态: §f" + obj.get("status").getAsString());
            if (obj.has("banReason") && !obj.get("banReason").isJsonNull()) {
                reply(sender, "§e封禁原因: §c" + obj.get("banReason").getAsString());
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
                reply(sender, "§6[DreamPort] §c后端不可达");
                return;
            }
            JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
            reply(sender, "§6[DreamPort] " + (obj.get("ok").getAsBoolean()
                    ? "§a" + obj.get("message").getAsString()
                    : "§c" + obj.get("message").getAsString()));
        });
    }

    private void async(CommandSender sender, Runnable runnable) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    /** 主线程路由发送:玩家消息须回主线程(Folia 下异步线程发消息非法,修复审计 M4);控制台可直接发 */
    private void reply(CommandSender sender, String message) {
        if (sender instanceof org.bukkit.entity.Player) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin,
                    () -> sender.sendMessage(message));
        } else {
            sender.sendMessage(message);
        }
    }

    /**
     * QQ 验证绑定游戏内确认（docs/ASTRBOT_PLAN.md §5.2）：
     * /xmw qq bind <验证码> —— 验证码经 QQ 机器人 /dp绑定 获取，网页或游戏内任一通道确认。
     */
    /** 游戏内签到(积分任务,每日一次) */
    private void handleSignin(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            reply(sender, "§6[DreamPort] §c只有玩家可以签到");
            return;
        }
        async(sender, () -> {
            String body = plugin.backendClient().gameSignin(player.getName());
            if (body == null) {
                reply(sender, "§6[DreamPort] §c签到失败: 后端不可达");
                return;
            }
            try {
                JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
                boolean first = obj.get("first").getAsBoolean();
                String message = obj.get("message").getAsString();
                if (first) {
                    reply(sender, "§6[DreamPort] §a" + message);
                } else {
                    reply(sender, "§6[DreamPort] §e" + message);
                }
            } catch (Exception e) {
                reply(sender, "§6[DreamPort] §c签到响应解析失败");
            }
        });
    }

    private void handleQq(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            reply(sender, "§6[DreamPort] §c只有玩家可以绑定 QQ");
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("bind")) {
            usage(sender, "/xmw qq bind <验证码>");
            return;
        }
        String code = args[2];
        async(sender, () -> {
            String body = plugin.backendClient().qqBind(player.getName(), code);
            if (body == null) {
                reply(sender, "§6[DreamPort] §c后端不可达");
                return;
            }
            JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
            boolean ok = obj.has("success") && obj.get("success").getAsBoolean();
            String msg = obj.has("message") && !obj.get("message").isJsonNull()
                    ? obj.get("message").getAsString() : body;
            reply(sender, "§6[DreamPort] " + (ok ? "§a" + msg : "§c" + msg));
        });
    }

    private void noPermission(CommandSender sender) {
        reply(sender, "§6[DreamPort] §c没有权限");
    }

    // ---------- 奖励礼包采集 ----------

    /**
     * /xmw kit save <礼包名>:把当前背包 36 格内容上传为礼包模板(管理后台创建,采集后 ready 可发放);
     * /xmw kit list:查看礼包模板列表。物品经 ItemStack#serializeAsBytes 序列化为 Base64。
     */
    private void handleKit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dreamport.admin")) {
            noPermission(sender);
            return;
        }
        if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
            kitList(sender);
            return;
        }
        if (args.length >= 3 && "save".equalsIgnoreCase(args[1])) {
            kitSave(sender, args[2]);
            return;
        }
        usage(sender, "/xmw kit save <礼包名> | /xmw kit list");
    }

    /** 采集背包:主线程读 36 格并序列化(读背包要求主线程),上传在异步线程 */
    private void kitSave(CommandSender sender, String kitName) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            reply(sender, "§6[DreamPort] §c只有玩家可以采集背包");
            return;
        }
        var items = new ArrayList<java.util.Map<String, Object>>();
        StringBuilder summary = new StringBuilder();
        int count = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir() || it.getAmount() <= 0) {
                continue;
            }
            try {
                String b64 = java.util.Base64.getEncoder().encodeToString(it.serializeAsBytes());
                String rawName = it.hasItemMeta() && it.getItemMeta().hasDisplayName()
                        ? it.getItemMeta().getDisplayName() : it.getType().name();
                String name = (rawName == null || rawName.isBlank() ? it.getType().name() : rawName)
                        .replaceAll("§.", "");
                items.add(java.util.Map.of("s", b64, "n", name, "c", it.getAmount()));
                if (count < 6) {
                    if (count > 0) summary.append(", ");
                    summary.append(name).append("×").append(it.getAmount());
                }
                count++;
            } catch (Exception e) {
                reply(sender, "§6[DreamPort] §c物品序列化失败(" + it.getType() + "): " + e.getMessage());
                return;
            }
        }
        if (items.isEmpty()) {
            reply(sender, "§6[DreamPort] §c背包为空:请先把礼包物品放进背包再执行采集");
            return;
        }
        if (count > 6) {
            summary.append(" 等 ").append(count).append(" 组");
        }
        String itemsJson = plugin.backendClient().gson().toJson(items);
        String summaryStr = summary.toString();
        async(sender, () -> {
            String body = plugin.backendClient().kitSave(kitName, player.getName(), itemsJson, summaryStr);
            if (body == null) {
                reply(sender, "§6[DreamPort] §c上传失败: 后端不可达");
                return;
            }
            try {
                JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
                boolean ok = obj.has("success") && obj.get("success").getAsBoolean();
                String msg = obj.has("message") && !obj.get("message").isJsonNull()
                        ? obj.get("message").getAsString() : body;
                reply(sender, "§6[DreamPort] " + (ok ? "§a" + msg : "§c" + msg));
            } catch (Exception e) {
                reply(sender, "§6[DreamPort] §c上传响应解析失败");
            }
        });
    }

    /** 礼包模板列表(名称/状态/内容概要) */
    private void kitList(CommandSender sender) {
        async(sender, () -> {
            String body = plugin.backendClient().kitList();
            if (body == null) {
                reply(sender, "§6[DreamPort] §c后端不可达");
                return;
            }
            try {
                JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
                JsonArray kits = obj.has("kits") ? obj.getAsJsonArray("kits") : new JsonArray();
                if (kits.isEmpty()) {
                    reply(sender, "§6[DreamPort] §7暂无礼包模板(请在管理后台创建)");
                    return;
                }
                reply(sender, "§6[DreamPort] 礼包模板 (" + kits.size() + "):");
                for (var k : kits) {
                    JsonObject o = k.getAsJsonObject();
                    String status = o.get("status").getAsString();
                    String statusText = switch (status) {
                        case "ready" -> "§a可发放";
                        case "disabled" -> "§c已停用";
                        default -> "§e待采集";
                    };
                    String summary = o.has("summary") && !o.get("summary").isJsonNull()
                            ? o.get("summary").getAsString() : "";
                    reply(sender, "§e- " + o.get("name").getAsString() + " " + statusText
                            + (summary.isBlank() ? "" : " §7" + summary));
                }
                reply(sender, "§7采集: 把物品放进背包后执行 /xmw kit save <礼包名>");
            } catch (Exception e) {
                reply(sender, "§6[DreamPort] §c礼包列表解析失败");
            }
        });
    }

    private void usage(CommandSender sender, String usage) {
        reply(sender, "§f用法: " + usage);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUB.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && "qq".equalsIgnoreCase(args[0])) {
            return List.of("bind");
        }
        if (args.length == 2 && "kit".equalsIgnoreCase(args[0])) {
            return List.of("save", "list");
        }
        return List.of();
    }
}
