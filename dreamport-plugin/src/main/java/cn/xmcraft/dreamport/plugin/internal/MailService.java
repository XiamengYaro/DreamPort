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

    public record PendingMail(long id, String title, String note, String commandsJson, String itemsJson) {}

    /** GUI 会话持有者:同一封邮件在同一 GUI 会话中只领取一次 */
    public static final class MailHolder implements org.bukkit.inventory.InventoryHolder {
        public final List<PendingMail> mails;

        MailHolder(List<PendingMail> mails) {
            this.mails = mails;
        }

        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return null;
        }
    }

    private final DreamPortPlugin plugin;

    /** 修复审计 H2：跨 GUI 会话的已领取集合(原仅 MailHolder 会话内去重,重开 /mail 可双倍奖励) */
    private final java.util.Set<Long> claimedIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
                long id = o.get("id").getAsLong();
                if (claimedIds.contains(id)) {
                    continue; // 已在本进程领取过,不再展示,避免重开 GUI 后重复领取
                }
                mails.add(new PendingMail(id,
                        o.get("title").getAsString(),
                        o.has("note") && !o.get("note").isJsonNull() ? o.get("note").getAsString() : "",
                        o.has("commands") && !o.get("commands").isJsonNull() ? o.get("commands").toString() : "{}",
                        o.has("items") && !o.get("items").isJsonNull() ? o.get("items").getAsString() : ""));
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

    /**
     * 领取一封邮件(须主线程调用——物品反序列化/入包/空间检查都要主线程)。
     * 物品部分(items 非空):整包反序列化 → 空间检查(不足则拒绝且邮件保留)→ 入包;
     * 指令部分(commands):回主线程调度执行;全部完成后异步回执。
     *
     * @return true=受理(调用方应清空 GUI 槽位);false=拒绝(邮件保留,可清理背包后重试)
     */
    public boolean claim(Player player, PendingMail mail) {
        // 修复审计 H2：同步登记,跨会话/并发第二次领取直接拒绝
        if (!claimedIds.add(mail.id())) {
            return false;
        }
        // ---- 物品直发(礼包奖励):反序列化全部成功才入包,任一失败整包拒绝 ----
        if (mail.itemsJson() != null && !mail.itemsJson().isBlank()) {
            List<ItemStack> items = new ArrayList<>();
            try {
                var arr = plugin.backendClient().gson().fromJson(mail.itemsJson(), JsonArray.class);
                if (arr != null) {
                    for (var e : arr) {
                        JsonObject o = e.getAsJsonObject();
                        byte[] data = java.util.Base64.getDecoder().decode(o.get("s").getAsString());
                        ItemStack it = ItemStack.deserializeBytes(data);
                        if (it != null && !it.getType().isAir() && it.getAmount() > 0) {
                            items.add(it);
                        }
                    }
                }
            } catch (Exception e) {
                claimedIds.remove(mail.id());
                plugin.getLogger().warning("礼包物品解析失败(邮件 " + mail.id() + "): " + e.getMessage());
                player.sendMessage("§c[DreamPort] 礼包内容无法读取,邮件已保留,请联系管理员");
                return false;
            }
            // 空间检查:空槽位数 ≥ 待放堆数(保守检查;可并堆场景实际更宽松)
            int free = 0;
            for (ItemStack s : player.getInventory().getStorageContents()) {
                if (s == null || s.getType().isAir()) {
                    free++;
                }
            }
            if (free < items.size()) {
                claimedIds.remove(mail.id());
                player.sendMessage("§e[DreamPort] 背包空间不足(还需 " + (items.size() - free)
                        + " 格),清理后重新点击领取");
                return false;
            }
            for (ItemStack it : items) {
                player.getInventory().addItem(it);
            }
        }
        // ---- 指令部分:回主线程调度执行(单条异常不中断),完成后异步回执 ----
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                int executed = 0;
                if (mail.commandsJson() != null && !mail.commandsJson().isBlank()) {
                    JsonArray commands = commandsOf(mail.commandsJson());
                    for (var c : commands) {
                        try {
                            JsonObject o = c.getAsJsonObject();
                            String type = o.has("type") ? o.get("type").getAsString() : "command";
                            if ("deposit".equals(type)) {
                                vaultDeposit(player, o.get("amount").getAsDouble());
                                executed++;
                            } else if (o.has("cmd")) {
                                String cmd = o.get("cmd").getAsString().replace("{player}", player.getName());
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                                executed++;
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("奖励指令执行失败(" + mail.id() + "): " + e.getMessage());
                        }
                    }
                }
                player.sendMessage("§a已领取:" + mail.title()
                        + (mail.itemsJson() == null || mail.itemsJson().isBlank() ? "" : " §7(物品已放入背包)"));
                if (executed > 0) {
                    plugin.getLogger().info("邮件 " + mail.id() + " 指令执行 " + executed + " 条 → " + player.getName());
                }
                plugin.getServer().getAsyncScheduler().runNow(plugin, t2 ->
                        plugin.backendClient().mailClaimed(mail.id()));
            } catch (Exception e) {
                plugin.getLogger().warning("邮件领取处理失败(" + mail.id() + "): " + e.getMessage());
            }
        });
        return true;
    }

    /** 指令 JSON 解析:兼容 {"commands":[...]}(商店/后台格式)与裸 [...](容错) */
    private JsonArray commandsOf(String commandsJson) {
        try {
            var el = com.google.gson.JsonParser.parseString(commandsJson);
            if (el.isJsonObject() && el.getAsJsonObject().has("commands")) {
                return el.getAsJsonObject().getAsJsonArray("commands");
            }
            if (el.isJsonArray()) {
                return el.getAsJsonArray();
            }
        } catch (Exception ignored) {
        }
        return new JsonArray();
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
