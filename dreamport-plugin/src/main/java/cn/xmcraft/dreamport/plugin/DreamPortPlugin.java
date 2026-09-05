package cn.xmcraft.dreamport.plugin;

import cn.xmcraft.dreamport.plugin.command.XmwCommand;
import cn.xmcraft.dreamport.plugin.internal.BackendClient;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DreamPort 薄插件入口（三角色：primary / secondary / proxy，见 config.yml role）。
 * P1 提供骨架与连通性命令；监听器与上报在 P5 接入（进度表 5-2..5-12）。
 */
public final class DreamPortPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private BackendClient backendClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = PluginConfig.load(getConfig());
        backendClient = new BackendClient(this);

        CommandExecutor executor = new XmwCommand(this);
        var command = getCommand("xmw");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor instanceof org.bukkit.command.TabCompleter t ? t : null);
        }

        getLogger().info("DreamPort v" + getDescription().getVersion()
                + " 已启动（role=" + pluginConfig.role()
                + ", backend=" + pluginConfig.backendUrl() + "）");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        pluginConfig = PluginConfig.load(getConfig());
        backendClient = new BackendClient(this);
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public BackendClient backendClient() {
        return backendClient;
    }
}
