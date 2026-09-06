package cn.xmcraft.dreamport.plugin;

import cn.xmcraft.dreamport.plugin.command.XmwCommand;
import cn.xmcraft.dreamport.plugin.i18n.I18nManager;
import cn.xmcraft.dreamport.plugin.internal.BackendClient;
import cn.xmcraft.dreamport.plugin.listener.LoginListener;
import cn.xmcraft.dreamport.plugin.listener.PlayerEventsListener;
import cn.xmcraft.dreamport.plugin.tasks.ScheduledTasks;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DreamPort 薄插件入口（三角色：primary / secondary / proxy，见 config.yml role）。
 * - primary：进服校验（缓存+fail_policy）、登录记录、事件上报、经济快照、whitelist 指令
 * - secondary：仅心跳/事件上报（合并旧版 Bridge）
 * - proxy：Velocity 端统一拦截由 dreamport-plugin-proxy 承担；Paper 端 role=proxy 等同上报模式
 */
public final class DreamPortPlugin extends JavaPlugin {

    private static final String[] BANNER_LINES = {
            " _____                           _____           _   ",
            " |  __ \\                         |  __ \\         | |  ",
            " | |  | |_ __ ___  __ _ _ __ ___ | |__) |__  _ __| |_ ",
            " | |  | | '__/ _ \\/ _` | '_ ` _ \\|  ___/ _ \\| '__| __|",
            " | |__| | | |  __/ (_| | | | | | | |  | (_) | |  | |_ ",
            "  |_____/|_|  \\___|\\__,_|_| |_| |_|_|   \\___/|_|   \\__|"
    };

    private PluginConfig pluginConfig;
    private BackendClient backendClient;
    private I18nManager i18n;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        printBanner();

        saveDefaultConfig();
        pluginConfig = PluginConfig.load(getConfig());
        i18n = new I18nManager(this, getConfig().getString("language", "zh"));
        backendClient = new BackendClient(this);

        // 监听器与命令
        getServer().getPluginManager().registerEvents(new LoginListener(this, i18n), this);
        getServer().getPluginManager().registerEvents(new PlayerEventsListener(this), this);
        CommandExecutor executor = new XmwCommand(this);
        var command = getCommand("xmw");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor instanceof TabCompleter t ? t : null);
        }

        // 周期任务
        ScheduledTasks.start(this);

        // bStats 匿名统计（可在配置关闭）
        if (getConfig().getBoolean("bstats.enabled", true)) {
            try {
                Class.forName("org.bstats.bukkit.Metrics")
                        .getConstructor(JavaPlugin.class, int.class)
                        .newInstance(this, 20000);
            } catch (Exception e) {
                getLogger().info("bStats 未启用: " + e.getMessage());
            }
        }

        printStartupReport(start);
    }

    /** 字符画 Banner（figlet Standard 字体，纯 ASCII） */
    private void printBanner() {
        getLogger().info("");
        for (String line : BANNER_LINES) {
            getLogger().info(line);
        }
        getLogger().info("    夏日小镇 · 梦港  ——  DreamPort Paper 插件");
        getLogger().info("");
    }

    /** 中文启动记录 */
    private void printStartupReport(long startMillis) {
        var cfg = pluginConfig();
        getLogger().info("┌──────────────────── 启动记录 ────────────────────┐");
        report("插件版本", "v" + getDescription().getVersion());
        report("运行角色", switch (cfg.role()) {
            case "primary" -> "primary（主服：进服拦截 + 事件上报）";
            case "secondary" -> "secondary（子服：仅状态上报）";
            case "proxy" -> "proxy（上报模式，由代理统一拦截）";
            default -> cfg.role();
        });
        report("后端地址", cfg.backendUrl());
        report("服务器标识", cfg.serverId());
        report("白名单拦截", cfg.enforceWhitelist() ? "开启（fail-policy: " + cfg.failPolicy() + "）" : "关闭");
        report("聊天互通", cfg.forwardChat() ? "开启" : "关闭");
        report("进出服播报", cfg.reportJoinQuit() ? "开启" : "关闭");
        report("运行平台", getServer().getName() + " " + getServer().getBukkitVersion());
        double seconds = (System.currentTimeMillis() - startMillis) / 1000.0;
        report("启动耗时", String.format("%.1f 秒", seconds));
        getLogger().info("└──────────────────────────────────────────────────┘");
        getLogger().info("");
    }

    private void report(String key, String value) {
        int pad = Math.max(1, 16 - key.length() - countCjk(key));
        getLogger().info("│ ✓ " + key + " ".repeat(pad) + value);
    }

    private int countCjk(String s) {
        int n = 0;
        for (char c : s.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {
                n++;
            }
        }
        return n;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        pluginConfig = PluginConfig.load(getConfig());
        i18n = new I18nManager(this, getConfig().getString("language", "zh"));
        backendClient = new BackendClient(this);
    }

    public I18nManager i18n() {
        return i18n;
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public BackendClient backendClient() {
        return backendClient;
    }
}
