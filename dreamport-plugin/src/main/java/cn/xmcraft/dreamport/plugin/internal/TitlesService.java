package cn.xmcraft.dreamport.plugin.internal;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 称号系统(插件侧):
 * - 进服/周期/佩戴变更时从后端拉取佩戴称号并缓存,经 PlaceholderAPI 扩展暴露
 *   %dreamport_title% 等变量(不直接改 Tab/头顶,由聊天/Tab 插件消费占位符)
 * - /titles GUI:列出已拥有称号,点击佩戴/脱下(克隆邮件 GUI 模式)
 */
public final class TitlesService {

    /** 佩戴中的称号(code 可为空串 = 未佩戴) */
    public record ActiveTitle(String code, String name, String color) {}

    /** GUI 会话持有者 */
    public static final class TitlesHolder implements org.bukkit.inventory.InventoryHolder {
        public final List<ActiveTitle> titles;
        public final String activeCode;

        TitlesHolder(List<ActiveTitle> titles, String activeCode) {
            this.titles = titles;
            this.activeCode = activeCode;
        }

        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return null;
        }
    }

    private final DreamPortPlugin plugin;
    /** 玩家名(小写) → 佩戴称号;未佩戴玩家无条目 */
    private final Map<String, ActiveTitle> activeByPlayer = new ConcurrentHashMap<>();

    public TitlesService(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    public ActiveTitle active(String playerName) {
        return activeByPlayer.get(playerName.toLowerCase());
    }

    /** 进服时拉取佩戴称号(异步) */
    public void applyOnJoin(Player player) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                fetchAndCache(player.getName()));
    }

    /** 周期刷新在线玩家佩戴(网页佩戴变更 ≤1 分钟同步进服) */
    public void refreshOnline() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                    fetchAndCache(p.getName()));
        }
    }

    private void fetchAndCache(String name) {
        String body = plugin.backendClient().getInternal(
                "/internal/v1/title/active?username=" + urlEncode(name));
        if (body == null) return;
        try {
            JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
            if (obj == null || !obj.has("success")) return;
            String code = str(obj, "code");
            if (code.isBlank()) {
                activeByPlayer.remove(name.toLowerCase());
            } else {
                activeByPlayer.put(name.toLowerCase(),
                        new ActiveTitle(code, str(obj, "name"), str(obj, "color")));
            }
        } catch (Exception ignored) {
        }
    }

    private record OwnedTitle(String code, String name, String desc, String color) {}

    private record MineResult(List<OwnedTitle> owned, String activeCode) {}

    private MineResult fetchMine(String name) {
        List<OwnedTitle> owned = new ArrayList<>();
        String activeCode = "";
        String body = plugin.backendClient().getInternal(
                "/internal/v1/title/mine?username=" + urlEncode(name));
        try {
            JsonObject obj = plugin.backendClient().gson().fromJson(body, JsonObject.class);
            if (obj != null && obj.has("titles")) {
                activeCode = str(obj, "active");
                for (var e : obj.getAsJsonArray("titles")) {
                    JsonObject o = e.getAsJsonObject();
                    owned.add(new OwnedTitle(str(o, "code"), str(o, "name"),
                            str(o, "desc"), str(o, "color")));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("称号列表拉取失败: " + e.getMessage());
        }
        return new MineResult(owned, activeCode);
    }

    /** 打开称号 GUI(异步拉取 → 主线程渲染) */
    public void openGui(Player player) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            MineResult mine = fetchMine(player.getName());
            List<ActiveTitle> titles = mine.owned().stream()
                    .map(o -> new ActiveTitle(o.code(), o.name(), o.color())).toList();
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                int size = Math.max(9, ((mine.owned().size() + 8) / 9) * 9);
                Inventory inv = Bukkit.createInventory(new TitlesHolder(titles, mine.activeCode()),
                        size, "§6我的称号");
                for (int i = 0; i < mine.owned().size() && i < size; i++) {
                    OwnedTitle o = mine.owned().get(i);
                    boolean equipped = o.code().equals(mine.activeCode());
                    ItemStack item = new ItemStack(Material.NAME_TAG);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(hexColor(o.color()) + o.name()
                            + (equipped ? " §a✔" : ""));
                    List<String> lore = new ArrayList<>();
                    if (!o.desc().isBlank()) lore.add("§7" + o.desc());
                    lore.add(equipped ? "§e点击脱下" : "§a点击佩戴");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                    inv.setItem(i, item);
                }
                player.openInventory(inv);
            });
        });
    }

    /** 佩戴/脱下(code 空 = 脱下)并刷新本地缓存 */
    public void equip(Player player, String code) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            String body = plugin.backendClient().post("/internal/v1/title/equip",
                    Map.of("username", player.getName(), "code", code == null ? "" : code));
            boolean ok = body != null && body.contains("\"success\":true");
            String msg = ok
                    ? (code == null || code.isBlank() ? "已脱下称号" : "已佩戴称号")
                    : "操作失败,请稍后再试";
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                player.sendMessage("§6[DreamPort] §" + (ok ? "a" : "c") + msg);
                if (ok) {
                    openGui(player);
                }
            });
            fetchAndCache(player.getName());
        });
    }

    /** %dreamport_title% 显示串:着色「[名] 」前缀;未佩戴返回空串 */
    public String displayPrefix(String playerName) {
        ActiveTitle t = active(playerName);
        return t == null ? "" : hexColor(t.color()) + "[" + t.name() + "]§r ";
    }

    /** %dreamport_title_raw%:仅着色称号名;未佩戴返回空串 */
    public String displayName(String playerName) {
        ActiveTitle t = active(playerName);
        return t == null ? "" : hexColor(t.color()) + t.name() + "§r";
    }

    /** #rrggbb → MC §x RGB 序列;非法值回落金色 */
    public static String hexColor(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            return "§6";
        }
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.substring(1).toCharArray()) {
            sb.append('§').append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String str(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
