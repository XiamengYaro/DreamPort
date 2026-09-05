package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.economy.EconomyService;
import cn.xmcraft.dreamport.server.stats.ServerStatsService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经济/时长榜单（契约对齐旧版 /api/cmi/*，数据源为插件经济快照——Rules.md §9-2 真实统计）。
 * 榜单条目字段: name / balance / timePlayed(秒) / activeDays(天)。
 */
@RestController
@RequestMapping("/api/cmi")
public class EconomyController {

    private final EconomyService economyService;
    private final ServerStatsService statsService;
    private final UserRepository userRepository;

    public EconomyController(EconomyService economyService, ServerStatsService statsService,
                             UserRepository userRepository) {
        this.economyService = economyService;
        this.statsService = statsService;
        this.userRepository = userRepository;
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }

    private Map<String, Object> diagnostics() {
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("provider", "dreamport-snapshot");
        diag.put("snapshotSize", economyService.snapshot().size());
        return diag;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", true);
        data.put("totalPlayers", userRepository.count());
        data.put("onlinePlayers", statsService.totalOnline());
        data.put("_diagnostics", diagnostics());
        return ok(data);
    }

    @GetMapping("/stats/extended")
    public Map<String, Object> extended() {
        Map<String, Object> data = new LinkedHashMap<>(economyService.extendedStats((int) userRepository.count()));
        data.put("onlinePlayers", statsService.totalOnline());
        data.put("_diagnostics", diagnostics());
        return ok(data);
    }

    @GetMapping("/wealth")
    public Map<String, Object> wealth(@RequestParam(defaultValue = "10") int limit) {
        return ok(Map.of("list", economyService.leaderboard("balance", limit)));
    }

    @GetMapping("/playtime")
    public Map<String, Object> playtime(@RequestParam(defaultValue = "10") int limit) {
        return ok(Map.of("list", economyService.leaderboard("playtime", limit)));
    }

    @GetMapping("/activedays")
    public Map<String, Object> activeDays(@RequestParam(defaultValue = "10") int limit) {
        return ok(Map.of("list", economyService.leaderboard("activedays", limit)));
    }

    @GetMapping("/online")
    public Map<String, Object> online() {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for (ServerStatsService.Heartbeat h : statsService.heartbeats().values()) {
            names.addAll(h.players());
        }
        return ok(Map.of("list", names));
    }

    @GetMapping("/banned")
    public Map<String, Object> banned() {
        List<Map<String, Object>> list = userRepository.listAll().stream()
                .filter(u -> "banned".equals(u.status()))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", u.username());
                    m.put("banReason", u.banReason() == null ? "" : u.banReason());
                    return m;
                }).toList();
        return ok(Map.of("list", list));
    }

    @GetMapping("/player/{name}")
    public Map<String, Object> player(@PathVariable String name) {
        return ok(economyService.playerData(name));
    }
}
