package cn.xmcraft.dreamport.plugin.internal;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 经济数据采集（快照上报，修复旧版直连第三方数据库的设计）：
 * 1) Vault API（反射，避免硬依赖）优先
 * 2) 回退 EssentialsX userdata（plugins/Essentials/userdata/&lt;uuid&gt;.json 的 money/onlinetime）
 * cyutime 数据可经 dataFolder/cyutime.db 提示，不在本采集器强制支持。
 */
public final class EconomyCollector {

    private final DreamPortPlugin plugin;

    public EconomyCollector(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    public void collectAndReport() {
        JsonArray players = new JsonArray();
        int collected = 0;
        // Vault 反射通道
        Object economy = vaultEconomy();
        if (economy != null) {
            for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                try {
                    double balance = invokeBalance(economy, p);
                    players.add(entry(p, balance,
                            Math.max(essentialsPlaytime(p.getUniqueId()), bukkitPlaytimeSeconds(p)),
                            bukkitLoginCount(p)));
                    collected++;
                } catch (Exception ignored) {
                }
            }
        } else {
            // Essentials userdata 回退
            Path userdata = Path.of("plugins", "Essentials", "userdata");
            if (Files.isDirectory(userdata)) {
                try (var stream = Files.list(userdata)) {
                    for (Path file : stream.filter(f -> f.toString().endsWith(".json")).toList()) {
                        try {
                            String json = Files.readString(file);
                            JsonObject obj = plugin.backendClient().gson().fromJson(json, JsonObject.class);
                            if (obj != null && obj.has("money")) {
                                String uuid = file.getFileName().toString().replace(".json", "");
                                OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                                players.add(entry(p, obj.get("money").getAsDouble(),
                                        Math.max(obj.has("onlinetime") ? obj.get("onlinetime").getAsLong() : 0,
                                                bukkitPlaytimeSeconds(p)),
                                        bukkitLoginCount(p)));
                                collected++;
                            }
                        } catch (IOException | IllegalArgumentException ignored) {
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        }
        JsonObject payload = new JsonObject();
        payload.add("players", players);
        plugin.backendClient().economySnapshot(payload.toString());
        plugin.getLogger().info("经济快照已上报（" + collected + " 名玩家）");
    }

    private JsonObject entry(OfflinePlayer p, double balance, long playtimeSeconds, long loginCount) {
        JsonObject o = new JsonObject();
        o.addProperty("name", p.getName() == null ? p.getUniqueId().toString() : p.getName());
        o.addProperty("balance", balance);
        o.addProperty("playtimeSeconds", playtimeSeconds);
        o.addProperty("playtimeDays", playtimeSeconds / 86400);
        o.addProperty("lastLogin", p.getLastPlayed());
        o.addProperty("loginCount", loginCount);
        return o;
    }

    private Object vaultEconomy() {
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            var rsp = Bukkit.getServicesManager().getRegistration(econClass);
            return rsp == null ? null : rsp.getProvider();
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return null;
        }
    }

    private double invokeBalance(Object economy, OfflinePlayer p) throws Exception {
        var method = economy.getClass().getMethod("getBalance", OfflinePlayer.class);
        Object result = method.invoke(economy, p);
        return result instanceof Number n ? n.doubleValue() : 0;
    }

    private long essentialsPlaytime(UUID uuid) {
        try {
            Path file = Path.of("plugins", "Essentials", "userdata", uuid + ".json");
            if (Files.exists(file)) {
                JsonObject obj = plugin.backendClient().gson().fromJson(Files.readString(file),
                        JsonObject.class);
                if (obj != null && obj.has("onlinetime")) {
                    return obj.get("onlinetime").getAsLong();
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /** Bukkit 原生统计兜底:总在线时长(TOTAL_WORLD_TIME,20 tick/秒),无需 Essentials */
    private long bukkitPlaytimeSeconds(OfflinePlayer p) {
        try {
            return p.getStatistic(Statistic.TOTAL_WORLD_TIME) / 20L;
        } catch (IllegalArgumentException ignored) {
            // 玩家从未进服,无统计数据
        }
        return 0;
    }

    /** Bukkit 原生统计:退出游戏次数≈登录次数 */
    private long bukkitLoginCount(OfflinePlayer p) {
        try {
            return p.getStatistic(Statistic.LEAVE_GAME);
        } catch (IllegalArgumentException ignored) {
        }
        return 0;
    }
}
