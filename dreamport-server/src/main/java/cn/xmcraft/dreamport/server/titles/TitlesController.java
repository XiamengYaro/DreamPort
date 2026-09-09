package cn.xmcraft.dreamport.server.titles;

import cn.xmcraft.dreamport.server.notification.NotificationRecord;
import cn.xmcraft.dreamport.server.notification.NotificationRepository;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 称号与成就(JWT 用户端 + 管理端)。
 * 定义存 dp_setting titles.config / achievements.config(JSON,后台可视化 CRUD);
 * 拥有/佩戴记录在 dp_user_titles / dp_title_equipped(V9)。
 */
@RestController
public class TitlesController {

    private final cn.xmcraft.dreamport.server.titles.TitleService titleService;
    private final cn.xmcraft.dreamport.server.notification.NotificationRepository notificationRepository;

    public TitlesController(cn.xmcraft.dreamport.server.titles.TitleService titleService,
                            cn.xmcraft.dreamport.server.notification.NotificationRepository notificationRepository) {
        this.titleService = titleService;
        this.notificationRepository = notificationRepository;
    }

    private ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(401).body(ApiResponse.failure("请先登录"));
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
    }

    // ---------- 用户端 ----------

    /** 我的称号:定义 + 已拥有 + 当前佩戴 + 成就进度 */
    @GetMapping("/api/titles/mine")
    public ResponseEntity<Object> mine(jakarta.servlet.http.HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) return unauthorized();
        titleService.evaluate(me);
        List<TitleService.TitleDef> defs = titleService.titles();
        Map<String, Long> ownedAt = new LinkedHashMap<>();
        for (var row : titleService.ownedRows(me)) {
            ownedAt.put(row.get("title_code").toString(), Long.parseLong(row.get("obtained_at").toString()));
        }
        String equippedRaw = titleService.activeTitleCode(me);
        // 已禁用/已删除定义的称号视为未佩戴,与 activeTitle/enabled 过滤口径一致
        String equipped = (equippedRaw != null && defs.stream().noneMatch(t -> t.code().equals(equippedRaw)))
                ? null : equippedRaw;

        List<Map<String, Object>> owned = new ArrayList<>();
        List<Map<String, Object>> locked = new ArrayList<>();
        for (var t : defs) {
            if (!t.enabled()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", t.code());
            item.put("name", t.name());
            item.put("desc", t.desc());
            item.put("color", t.color());
            if (ownedAt.containsKey(t.code())) {
                item.put("obtainedAt", ownedAt.get(t.code()));
                owned.add(item);
            } else {
                locked.add(item);
            }
        }

        List<Map<String, Object>> achievements = new ArrayList<>();
        for (var a : titleService.achievements()) {
            if (!a.enabled()) continue;
            int value = titleService.metricValue(me, a.metric());
            var ownedReward = owned.stream().filter(o -> o.get("code").equals(a.reward())).findFirst();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.id());
            item.put("name", a.name());
            item.put("desc", a.desc());
            item.put("metric", a.metric());
            item.put("target", a.target());
            item.put("progress", Math.min(value, a.target()));
            item.put("completed", value >= a.target());
            item.put("reward", a.reward());
            item.put("rewardOwned", ownedReward.isPresent());
            achievements.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("owned", owned);
        data.put("locked", locked);
        data.put("equipped", equipped);
        data.put("achievements", achievements);
        return ResponseEntity.ok(ApiResponse.success(null, data));
    }

    /** 佩戴称号 */
    @PostMapping("/api/titles/equip")
    public ResponseEntity<Object> equip(@RequestBody Map<String, Object> body,
                                        jakarta.servlet.http.HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) return unauthorized();
        String code = String.valueOf(body.getOrDefault("code", ""));
        try {
            titleService.equip(me, code);
            return ResponseEntity.ok(ApiResponse.success("已佩戴"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    /** 脱下称号 */
    @PostMapping("/api/titles/unequip")
    public ResponseEntity<Object> unequip(jakarta.servlet.http.HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) return unauthorized();
        titleService.unequip(me);
        return ResponseEntity.ok(ApiResponse.success("已脱下"));
    }

    // ---------- 管理端 ----------

    private boolean admin(jakarta.servlet.http.HttpServletRequest request) {
        return AuthUtil.currentUser(request) != null && cn.xmcraft.dreamport.server.security.AuthUtil.isAdmin(request);
    }

    /** 总览:称号定义 + 成就定义 + 持有者计数 */
    @GetMapping("/api/admin/titles/overview")
    public ResponseEntity<Object> adminOverview(jakarta.servlet.http.HttpServletRequest request) {
        if (!admin(request)) return forbidden();
        var defs = titleService.titles();
        var achs = titleService.achievements();
        List<Map<String, Object>> titles = new ArrayList<>();
        for (var t : defs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", t.code());
            m.put("name", t.name());
            m.put("desc", t.desc());
            m.put("color", t.color());
            m.put("gameColor", t.gameColor());
            m.put("enabled", t.enabled());
            m.put("holders", titleService.holders(t.code()));
            titles.add(m);
        }
        List<Map<String, Object>> achievements = new ArrayList<>();
        for (var a : achs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id());
            m.put("name", a.name());
            m.put("desc", a.desc());
            m.put("metric", a.metric());
            m.put("target", a.target());
            m.put("reward", a.reward());
            m.put("enabled", a.enabled());
            achievements.add(m);
        }
        return ResponseEntity.ok(ApiResponse.success(null, Map.of("titles", titles, "achievements", achievements)));
    }

    /** 保存称号定义 */
    @PutMapping("/api/admin/settings/titlesconfig")
    public ResponseEntity<Object> saveTitlesConfig(@RequestBody java.util.List<Map<String, Object>> body,
                                                   jakarta.servlet.http.HttpServletRequest request) {
        if (!admin(request)) return forbidden();
        try {
            titleService.saveTitlesFromMaps(body);
            return ResponseEntity.ok(ApiResponse.success("称号定义已保存"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    /** 保存成就定义 */
    @PutMapping("/api/admin/settings/achievementsconfig")
    public ResponseEntity<Object> saveAchievementsConfig(@RequestBody java.util.List<Map<String, Object>> body,
                                                         jakarta.servlet.http.HttpServletRequest request) {
        if (!admin(request)) return forbidden();
        try {
            titleService.saveAchievementsFromMaps(body);
            return ResponseEntity.ok(ApiResponse.success("成就定义已保存"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    /** 手动授予称号 */
    @PostMapping("/api/admin/titles/grant")
    public ResponseEntity<Object> grant(@RequestBody Map<String, Object> body,
                                        jakarta.servlet.http.HttpServletRequest request) {
        if (!admin(request)) return forbidden();
        String username = String.valueOf(body.getOrDefault("username", ""));
        String code = String.valueOf(body.getOrDefault("code", ""));
        if (username.isBlank() || code.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("参数缺失"));
        }
        var userOpt = titleService.userExists(username);
        if (!userOpt) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("用户不存在"));
        }
        titleService.grant(username, code);
        notificationRepository.save(new cn.xmcraft.dreamport.server.notification.NotificationRecord(
                null, username, "achievement", "获得新称号", "管理员授予了你一个称号,可在控制台佩戴",
                null, null, username));
        return ResponseEntity.ok(ApiResponse.success("已授予"));
    }

    /** 撤销称号 */
    @PostMapping("/api/admin/titles/revoke")
    public ResponseEntity<Object> revoke(@RequestBody Map<String, Object> body,
                                         jakarta.servlet.http.HttpServletRequest request) {
        if (!admin(request)) return forbidden();
        String username = String.valueOf(body.getOrDefault("username", "")).trim();
        String code = String.valueOf(body.getOrDefault("code", "")).trim();
        if (username.isBlank() || code.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("参数缺失"));
        }
        if (!titleService.userExists(username)) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("用户不存在"));
        }
        if (!titleService.hasTitle(username, code)) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("该玩家未拥有该称号"));
        }
        titleService.revoke(username, code);
        return ResponseEntity.ok(ApiResponse.success("已撤销"));
    }
}
