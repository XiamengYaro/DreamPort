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
 * - proxy：Velocity/BungeeCord 端统一拦截由独立分发物承担；Paper 端 role=proxy 时等同于上报模式
 */
public final class DreamPortPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private BackendClient backendClient;
    private I18nManager i18n;

    @Override
    public void onEnable() {
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

        // bStats 匿名统计（吸收 参考项目；可在配置关闭）
        if (getConfig().getBoolean("bstats.enabled", true)) {
            try {
                Class.forName("org.bstats.bukkit.Metrics")
                        .getConstructor(JavaPlugin.class, int.class)
                        .newInstance(this, 20000);
            } catch (Exception e) {
                getLogger().info("bStats 未启用: " + e.getMessage());
            }
        }

        getLogger().info("DreamPort v" + getDescription().getVersion()
                + " 已启动（role=" + pluginConfig.role()
                + ", backend=" + pluginConfig.backendUrl() + "）");
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
