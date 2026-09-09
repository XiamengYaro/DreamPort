package cn.xmcraft.dreamport.plugin.internal;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI 扩展(仅在服务端装有 PlaceholderAPI 时注册):
 * - %dreamport_title%       着色「[称号] 」前缀(聊天/Tab 格式串直接拼)
 * - %dreamport_title_raw%   仅着色称号名
 * - %dreamport_title_code%  称号代码
 */
public final class TitlesExpansion extends PlaceholderExpansion {

    private final DreamPortPlugin plugin;

    public TitlesExpansion(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dreamport";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** 插件热载后占位符仍保留 */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        TitlesService.ActiveTitle t = plugin.titlesService().active(player.getName());
        return switch (params) {
            case "title" -> plugin.titlesService().displayPrefix(player.getName());
            case "title_raw" -> plugin.titlesService().displayName(player.getName());
            case "title_code" -> t == null ? "" : t.code();
            default -> null;
        };
    }
}
