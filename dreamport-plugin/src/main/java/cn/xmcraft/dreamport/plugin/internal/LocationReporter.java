package cn.xmcraft.dreamport.plugin.internal;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家个人位置采集与上报(网页地图「我的位置」数据源):
 * - 家:读 Essentials userdata <uuid>.json 的 homes 键(EssentialsX JSON 格式,模式同 EconomyCollector)
 * - 死亡:PlayerDeathEvent 内存记录最近一次(死亡即触发上报;重启后内存清空,上报省略 death 字段,后端保留旧值)
 * - 时机:join/quit/300s 在线扫/死亡即报;全部异步,features.report-locations 开关(默认开)
 */
public final class LocationReporter {

    public record HomeSpot(String name, String world, double x, double y, double z) {
    }

    public record DeathSpot(String world, double x, double y, double z, long diedAt) {
    }

    /** 后端 /internal/v1/locations 请求体(Gson 序列化,null 字段自动省略) */
    public record LocationsReport(String username, String uuid,
                                  List<HomeSpot> homes, DeathSpot death) {
    }

    private final DreamPortPlugin plugin;
    /** uuid → 最近死亡(内存态) */
    private final Map<UUID, DeathSpot> lastDeaths = new ConcurrentHashMap<>();

    public LocationReporter(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    public void onDeath(String name, UUID uuid, DeathSpot death) {
        lastDeaths.put(uuid, death);
        reportAsync(name, uuid);
    }

    /** join/quit/定时扫:采集家 + 内存死亡 → 上报 */
    public void reportAsync(String name, UUID uuid) {
        if (!plugin.pluginConfig().reportLocations()) {
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                List<HomeSpot> homes = readHomes(uuid);
                DeathSpot death = lastDeaths.get(uuid);
                plugin.backendClient().reportLocations(name, uuid.toString(), homes, death);
            } catch (Exception e) {
                plugin.getLogger().warning("位置上报失败 " + name + ": " + e.getMessage());
            }
        });
    }

    /** 读取 Essentials userdata 中该玩家的家;文件/解析任何异常都返回空列表 */
    public List<HomeSpot> readHomes(UUID uuid) {
        try {
            Path file = Path.of("plugins", "Essentials", "userdata", uuid + ".json");
            if (!Files.exists(file)) {
                return List.of();
            }
            JsonObject obj = plugin.backendClient().gson().fromJson(Files.readString(file), JsonObject.class);
            return parseHomes(obj);
        } catch (Exception e) {
            plugin.getLogger().warning("Essentials 家位置读取失败 " + uuid + ": " + e.getMessage());
            return List.of();
        }
    }

    /** 解析 userdata 的 homes 键(包内可见,单测用);缺字段/非法值跳过该条 */
    static List<HomeSpot> parseHomes(JsonObject userdata) {
        List<HomeSpot> homes = new ArrayList<>();
        if (userdata == null || !userdata.has("homes") || !userdata.get("homes").isJsonObject()) {
            return homes;
        }
        for (Map.Entry<String, JsonElement> e : userdata.getAsJsonObject("homes").entrySet()) {
            try {
                JsonObject h = e.getValue().getAsJsonObject();
                homes.add(new HomeSpot(
                        e.getKey(),
                        h.get("world").getAsString(),
                        h.get("x").getAsDouble(),
                        h.get("y").getAsDouble(),
                        h.get("z").getAsDouble()));
            } catch (Exception ignored) {
            }
        }
        return homes;
    }
}
