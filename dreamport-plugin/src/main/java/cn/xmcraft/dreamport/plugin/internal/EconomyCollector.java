package cn.xmcraft.dreamport.plugin.internal;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

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
                    players.add(entry(p, balance, essentialsPlaytime(p.getUniqueId())));
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
                                        obj.has("onlinetime") ? obj.get("onlinetime").getAsLong() : 0));
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

    private JsonObject entry(OfflinePlayer p, double balance, long playtimeSeconds) {
        JsonObject o = new JsonObject();
        o.addProperty("name", p.getName() == null ? p.getUniqueId().toString() : p.getName());
        o.addProperty("balance", balance);
        o.addProperty("playtimeSeconds", playtimeSeconds);
        o.addProperty("playtimeDays", playtimeSeconds / 86400);
        o.addProperty("lastLogin", p.getLastPlayed());
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
}
