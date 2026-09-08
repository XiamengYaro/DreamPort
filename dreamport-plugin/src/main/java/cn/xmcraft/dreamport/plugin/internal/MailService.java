package cn.xmcraft.dreamport.plugin.internal;

import cn.xmcraft.dreamport.plugin.DreamPortPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏内奖励邮件:网页兑换/后台发放 → 后端入队(dp_mail)→ 本服务拉取 → /mail GUI 领取。
 * 邮件 commands 为奖励对象 JSON(含 commands 数组:type=command 走控制台指令;
 * 兼容 type=deposit 走 Vault 反射存款)。{player} 占位符替换为领取玩家名。
 */
public final class MailService {

    public record PendingMail(long id, String title, String note, String commandsJson) {}

    /** GUI 会话持有者:同一封邮件在同一 GUI 会话中只领取一次 */
    public static final class MailHolder implements org.bukkit.inventory.InventoryHolder {
        public final List<PendingMail> mails;
        public final java.util.Set<Long> claimed = java.util.concurrent.ConcurrentHashMap.newKeySet();

        MailHolder(List<PendingMail> mails) {
            this.mails = mails;
        }

        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return null;
        }
    }

    private final DreamPortPlugin plugin;

    public MailService(DreamPortPlugin plugin) {
        this.plugin = plugin;
    }

    /** 拉取玩家待领取邮件;失败返回空列表 */
    public List<PendingMail> pending(String playerName) {
        List<PendingMail> mails = new ArrayList<>();
        String body = plugin.backendClient().mailPending(playerName);
        if (body == null) {
            return mails;
        }
        try {
            JsonObject data = plugin.backendClient().gson().fromJson(body, JsonObject.class).getAsJsonObject("data");
            for (var m : data.getAsJsonArray("mails")) {
                JsonObject o = m.getAsJsonObject();
                mails.add(new PendingMail(o.get("id").getAsLong(),
                        o.get("title").getAsString(),
                        o.has("note") ? o.get("note").getAsString() : "",
                        o.has("commands") ? o.get("commands").toString() : "{}"));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("奖励邮件拉取失败: " + e.getMessage());
        }
        return mails;
    }

    /** 进服延迟提醒(异步拉取 → 主线程消息) */
    public void notifyOnJoin(Player player) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            int n = pending(player.getName()).size();
            if (n > 0) {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin,
                        () -> player.sendMessage("§6[DreamPort] §e您有 " + n + " 封未领取的奖励邮件,输入 §6/mail §e领取"));
            }
        });
    }

    /** 打开邮箱 GUI(须主线程调用) */
    public void openGui(Player player) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            List<PendingMail> mails = pending(player.getName());
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                int size = Math.max(9, ((mails.size() + 8) / 9) * 9);
                Inventory inv = Bukkit.createInventory(new MailHolder(mails), size, "§6奖励邮箱");
                for (int i = 0; i < mails.size() && i < size; i++) {
                    PendingMail m = mails.get(i);
                    ItemStack item = new ItemStack(Material.PAPER);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName("§e" + m.title());
                    List<String> lore = new ArrayList<>();
                    if (m.note() != null && !m.note().isBlank()) lore.add("§7" + m.note());
                    lore.add("§a点击领取");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                    inv.setItem(i, item);
                }
                player.openInventory(inv);
            });
        });
    }

    /** 领取一封邮件:执行奖励指令/存款并回执(异步) */
    public void claim(Player player, PendingMail mail) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                JsonObject reward = plugin.backendClient().gson().fromJson(mail.commandsJson(), JsonObject.class);
                JsonArray commands = reward != null ? reward.getAsJsonArray("commands") : new JsonArray();
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    for (var c : commands) {
                        JsonObject o = c.getAsJsonObject();
                        String type = o.has("type") ? o.get("type").getAsString() : "command";
                        if ("deposit".equals(type)) {
                            vaultDeposit(player, o.get("amount").getAsDouble());
                        } else if (o.has("cmd")) {
                            String cmd = o.get("cmd").getAsString().replace("{player}", player.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        }
                    }
                    player.sendMessage("§a已领取:" + mail.title());
                });
                plugin.backendClient().mailClaimed(mail.id());
            } catch (Exception e) {
                plugin.getLogger().warning("邮件领取失败: " + e.getMessage());
            }
        });
    }

    /** Vault 存款(反射,镜像 EconomyCollector 模式;生产经济插件即 Vault provider) */
    private void vaultDeposit(OfflinePlayer p, double amount) {
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            var rsp = Bukkit.getServicesManager().getRegistration(econClass);
            if (rsp == null) return;
            Object economy = rsp.getProvider();
            economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class)
                    .invoke(economy, p, amount);
        } catch (Exception ignored) {
        }
    }

    /** 邮件列表是否为空(节流提示用) */
    public boolean hasPending(Player player) {
        return !pending(player.getName()).isEmpty();
    }

    /** 材料兜底(保留引用避免未用告警) */
    @SuppressWarnings("unused")
    private static Material placeholderMaterial() {
        return Material.PAPER;
    }
}
