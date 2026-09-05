package cn.xmcraft.dreamport.plugin.tasks;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 周期任务（Bukkit/Folia 统一走 AsyncScheduler）：
 * - 心跳上报（60s，三角色通用）
 * - whitelist 指令队列轮询（30s，bukkit 白名单模式）
 * - 经济快照采集（300s，primary）
 */
public final class ScheduledTasks {

    private ScheduledTasks() {
    }

    public static void start(DreamPortPlugin plugin) {
        var scheduler = plugin.getServer().getAsyncScheduler();
        var console = plugin.getServer().getConsoleSender();

        // 心跳
        runAtRate(plugin, 60, task -> {
            int online = plugin.getServer().getOnlinePlayers().size();
            int max = plugin.getServer().getMaxPlayers();
            java.util.List<String> players = plugin.getServer().getOnlinePlayers().stream()
                    .map(p -> p.getName()).toList();
            plugin.backendClient().heartbeat(online, max,
                    plugin.getServer().getBukkitVersion(), players);
        });

        // whitelist 指令队列（bukkit 白名单模式）
        runAtRate(plugin, 30, task -> {
            String body = plugin.backendClient().whitelistCommands();
            if (body == null || !body.contains("commands")) {
                return;
            }
            try {
                JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
                JsonArray commands = obj.getAsJsonArray("commands");
                if (commands != null) {
                    for (var cmd : commands) {
                        plugin.getServer().getGlobalRegionScheduler().execute(plugin,
                                () -> BukkitDispatch.dispatch(cmd.getAsString()));
                    }
                }
            } catch (Exception ignored) {
            }
        });

        // 经济快照
        runAtRate(plugin, 300, task -> new cn.xmcraft.dreamport.plugin.internal.EconomyCollector(plugin)
                .collectAndReport());

        // console 发送器引用
        BukkitDispatch.console = console;
    }

    private static void runAtRate(DreamPortPlugin plugin, long periodSeconds,
                                  Consumer<Object> task) {
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                t -> task.accept(null), periodSeconds, periodSeconds, TimeUnit.SECONDS);
    }

    /** 控制台指令执行器（主线程） */
    private static final class BukkitDispatch {
        static org.bukkit.command.CommandSender console;

        static void dispatch(String command) {
            if (console != null && command != null && !command.isBlank()) {
                org.bukkit.Bukkit.dispatchCommand(console, command);
            }
        }
    }
}
