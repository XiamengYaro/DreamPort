package cn.xmcraft.dreamport.server.economy;

import cn.xmcraft.dreamport.server.settings.SettingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经济/时长榜单（dp_setting 存最新快照——由插件采集 Vault/Essentials 数据上报，P5）。
 * 修复旧版假数据（chatCount=注册数×100、activeDays 恒 0，Rules.md §9-2）：
 * activeDays 由快照 playtimeDays 真实计算。
 */
@Service
public class EconomyService {

    private final SettingService settingService;

    public EconomyService(SettingService settingService) {
        this.settingService = settingService;
    }

    public record PlayerEconomy(String name, double balance, long playtimeSeconds,
                                long playtimeDays, long lastLogin) {
    }

    /** 插件上报快照（整体覆盖） */
    public void saveSnapshot(List<PlayerEconomy> snapshot) {
        settingService.set(SettingService.KEY_ECONOMY_SNAPSHOT, snapshot);
    }

    public List<PlayerEconomy> snapshot() {
        List<PlayerEconomy> list = settingService.get(SettingService.KEY_ECONOMY_SNAPSHOT, List.class);
        return list == null ? List.of() : list;
    }

    public List<PlayerEconomy> leaderboard(String metric, int limit) {
        Comparator<PlayerEconomy> comparator = switch (metric) {
            case "playtime" -> Comparator.comparingLong(PlayerEconomy::playtimeSeconds).reversed();
            case "activedays" -> Comparator.comparingLong(PlayerEconomy::playtimeDays).reversed();
            default -> Comparator.comparingDouble(PlayerEconomy::balance).reversed();
        };
        return snapshot().stream().sorted(comparator).limit(Math.max(1, limit)).toList();
    }

    public Map<String, Object> playerData(String username) {
        Map<String, Object> result = new LinkedHashMap<>();
        snapshot().stream()
                .filter(p -> p.name() != null && p.name().equalsIgnoreCase(username))
                .findFirst()
                .ifPresentOrElse(
                        p -> {
                            result.put("balance", p.balance());
                            result.put("playtimeSeconds", p.playtimeSeconds());
                            result.put("activeDaysLast30", p.playtimeDays());
                            result.put("lastLogin", p.lastLogin());
                        },
                        () -> result.put("found", false));
        return result;
    }

    public Map<String, Object> extendedStats(int registeredUsers) {
        List<PlayerEconomy> snapshot = snapshot();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPlayers", registeredUsers);
        stats.put("activePlayers", snapshot.size());
        stats.put("totalPlaytimeSeconds", snapshot.stream().mapToLong(PlayerEconomy::playtimeSeconds).sum());
        stats.put("totalBalance", snapshot.stream().mapToDouble(PlayerEconomy::balance).sum());
        stats.put("source", "plugin-snapshot");
        return stats;
    }

    @SuppressWarnings("unused")
    private static List<PlayerEconomy> copyOf(List<PlayerEconomy> list) {
        return new ArrayList<>(list);
    }
}
