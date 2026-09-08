package cn.xmcraft.dreamport.plugin.internal;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 称号系统(插件侧):
 * - 进服/佩戴变更时从后端拉取佩戴称号 → Scoreboard Team 前缀(Tab 列表与头顶可见)
 * - 团队命名 dp_title_<code>,同一称号共用一个 Team;佩戴变更时换 Team
 */
public final class TitlesService {

    private final DreamPortPlugin plugin;
    /** 玩家名 → 当前称号名(空串=未佩戴) */
    private final Map<String, String> activeByPlayer = new ConcurrentHashMap<>();

    public TitlesService(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    public String activeTitle(String playerName) {
        return activeByPlayer.get(playerName.toLowerCase());
    }

    /** 进服时应用佩戴称号(异步拉取 → 主线程设置 Team) */
    public void applyOnJoin(Player player) {
        String name = player.getName();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            String body = plugin.backendClient().getInternal(
                    "/internal/v1/title/active?username=" + urlEncode(name));
            if (body == null) return;
            try {
                JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
                String titleName = obj.has("name") && !obj.get("name").isJsonNull()
                        ? obj.get("name").getAsString() : "";
                if (!titleName.isBlank()) {
                    String finalName = titleName;
                    plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> applyPrefix(player, finalName));
                }
            } catch (Exception ignored) {
            }
        });
    }

    /** 设置称号前缀(Team 方式,主线程) */
    public void applyPrefix(Player player, String titleName) {
        var board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "dp_" + player.getName().toLowerCase();
        var team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        team.setPrefix("§6[" + titleName + "] §r");
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    private String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
