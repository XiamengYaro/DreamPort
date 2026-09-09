package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.common.HeartbeatRequest;
import cn.xmcraft.dreamport.common.HeartbeatResponse;
import cn.xmcraft.dreamport.common.LoginCheckRequest;
import cn.xmcraft.dreamport.common.ErrorCode;
import cn.xmcraft.dreamport.server.config.WlProps;
import cn.xmcraft.dreamport.server.economy.EconomyService;
import cn.xmcraft.dreamport.server.notification.NotificationRepository;
import cn.xmcraft.dreamport.server.qq.BindCodeService;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.qq.QqBindingService;
import cn.xmcraft.dreamport.server.qq.QqBridgeService;
import cn.xmcraft.dreamport.server.stats.ServerStatsService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.user.UserService;
import cn.xmcraft.dreamport.server.verification.MinecraftVerifyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务器间内部端点（鉴权：X-Server-Token，Rules.md §5）。
 * login-check / heartbeat / login-record / events / economy/snapshot / commands/whitelist。
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    private final UserService userService;
    private final WlProps props;
    private final ServerStatsService statsService;
    private final MinecraftVerifyService minecraftVerifyService;
    private final EconomyService economyService;
    private final cn.xmcraft.dreamport.server.infra.MailService mailService;
    private final cn.xmcraft.dreamport.server.chat.ChatService chatService;
    private final cn.xmcraft.dreamport.server.review.ReviewService reviewService;
    private final UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Value("${wl.economy.accept-from:}")
    private String economyAcceptFrom;

    private final SettingService settingService;
    private final NotificationRepository notificationRepository;
    private final BindCodeService bindCodeService;
    private final QqBindingService qqBindingService;
    private final QqBridgeService qqBridge;
    private final cn.xmcraft.dreamport.server.points.TaskService taskService;
    private final cn.xmcraft.dreamport.server.points.MailService rewardMailService;
    private final cn.xmcraft.dreamport.server.titles.TitleService titleService;
    private final cn.xmcraft.dreamport.server.reward.RewardKitService rewardKitService;

    public InternalController(UserService userService, WlProps props,
                              ServerStatsService statsService,
                              MinecraftVerifyService minecraftVerifyService,
                              EconomyService economyService,
                              cn.xmcraft.dreamport.server.infra.MailService mailService,
                              cn.xmcraft.dreamport.server.chat.ChatService chatService,
                              cn.xmcraft.dreamport.server.review.ReviewService reviewService,
                              UserRepository userRepository,
                              SettingService settingService,
                              NotificationRepository notificationRepository,
                              BindCodeService bindCodeService,
                              QqBindingService qqBindingService,
                              QqBridgeService qqBridge,
                              cn.xmcraft.dreamport.server.points.TaskService taskService,
                              cn.xmcraft.dreamport.server.points.MailService rewardMailService,
                              cn.xmcraft.dreamport.server.titles.TitleService titleService,
                              cn.xmcraft.dreamport.server.reward.RewardKitService rewardKitService) {
        this.userService = userService;
        this.props = props;
        this.statsService = statsService;
        this.minecraftVerifyService = minecraftVerifyService;
        this.economyService = economyService;
        this.mailService = mailService;
        this.chatService = chatService;
        this.reviewService = reviewService;
        this.userRepository = userRepository;
        this.settingService = settingService;
        this.notificationRepository = notificationRepository;
        this.bindCodeService = bindCodeService;
        this.qqBindingService = qqBindingService;
        this.qqBridge = qqBridge;
        this.taskService = taskService;
        this.rewardMailService = rewardMailService;
        this.titleService = titleService;
        this.rewardKitService = rewardKitService;
    }

    @PostMapping("/login-check")
    public ResponseEntity<Object> loginCheck(@RequestBody LoginCheckRequest req,
                                             HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (req == null || req.username() == null || req.username().isBlank()) {
            return badRequest("username 必填");
        }
        var decision = userService.loginDecision(req.username());
        if (decision.allowed()) {
            LOG.info("[校验] {} → 放行", req.username());
        } else {
            LOG.info("[校验] {} → 拒绝（{}）", req.username(), decision.reasonKey());
        }
        return ResponseEntity.ok(decision);
    }

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(InternalController.class);

    /** 各服上次心跳时间（用于离线检测） */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_SEEN = new java.util.concurrent.ConcurrentHashMap<>();

    @PostMapping("/heartbeat")
    public ResponseEntity<Object> heartbeat(@RequestBody HeartbeatRequest req,
                                            HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (req == null || req.serverId() == null || req.serverId().isBlank()) {
            return badRequest("serverId 必填");
        }
        long now = System.currentTimeMillis();
        String sid = req.serverId();
        Long prev = LAST_SEEN.put(sid, now);
        if (prev == null) {
            LOG.info("[连接] 服务器上线: {}（{}，{}）", sid, req.serverName(), req.role());
        }
        statsService.heartbeat(new ServerStatsService.Heartbeat(sid, req.serverName(),
                req.role(), req.onlinePlayers(), req.maxPlayers(), req.version(),
                req.players() == null ? java.util.List.of() : req.players(), now,
                req.tps1m(), req.tps5m(), req.tps15m(), req.avgTickMs(),
                req.memUsedMb(), req.memMaxMb(), req.cpuLoad(), req.uptimeSeconds()));
        if (now - (prev == null ? now : prev) >= 300_000) {
            // 每 5 分钟一条常规心跳摘要
            LOG.info("[心跳] {} 在线 {} / {}（玩家 {}）", sid, req.onlinePlayers(),
                    req.maxPlayers(), req.players() == null ? 0 : req.players().size());
        }
        return ResponseEntity.ok(new HeartbeatResponse(true, now));
    }

    /** 定时检测服务器离线（5 分钟无心跳视为离线） */
    private final java.util.Set<String> alertedOffline = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60_000)
    public void detectOffline() {
        long now = System.currentTimeMillis();
        LAST_SEEN.entrySet().removeIf(e -> {
            if (now - e.getValue() > 5 * 60_000L) {
                LOG.warn("[连接] 服务器离线: {}（超过 5 分钟无心跳）", e.getKey());
                alertServerOffline(e.getKey());
                alertedOffline.add(e.getKey());
                return true;
            }
            return false;
        });
        alertedOffline.removeIf(sid -> LAST_SEEN.containsKey(sid));
    }

    /** 服务器离线告警:通知 admins.list 各管理员(铃铛)+ 通知邮箱(admin_notify_email) */
    private void alertServerOffline(String serverId) {
        try {
            var admins = settingService.get(cn.xmcraft.dreamport.server.settings.SettingService.KEY_ADMINS, java.util.List.class);
            if (admins != null) {
                for (Object a : admins) {
                    notificationRepository.save(new cn.xmcraft.dreamport.server.notification.NotificationRecord(
                            null, String.valueOf(a), "server_offline", "服务器离线告警",
                            "服务器「" + serverId + "」超过 5 分钟无心跳,已从在线列表移除", null, null, null));
                }
            }
            String notifyEmail = settingService.get(cn.xmcraft.dreamport.server.settings.SettingService.KEY_ADMIN_NOTIFY_EMAIL, String.class);
            if (notifyEmail != null && !notifyEmail.isBlank()) {
                mailService.sendAdminNotification("服务器「" + serverId + "」超过 5 分钟无心跳,已离线。请检查服务器状态。", notifyEmail);
            }
        } catch (Exception e) {
            LOG.warn("离线告警发送失败: {}", e.getMessage());
        }
    }

    public record LoginRecordBody(String name, String uuid, String ip) {
    }

    /** QQ 绑定游戏内确认通道（/xmw qq bind <码>）：docs/ASTRBOT_PLAN.md §5.2 */
    public record QqBindBody(String player, String code) {
    }

    @PostMapping("/qq/bind")
    public ResponseEntity<Object> qqBind(@RequestBody QqBindBody body, HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (body == null || body.player() == null || body.code() == null || body.code().isBlank()) {
            return badRequest("player/code 必填");
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(body.player());
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByMinecraftNameIgnoreCase(body.player());
        }
        if (userOpt.isEmpty()) {
            return badRequest("账户不存在（需先注册白名单）");
        }
        var result = bindCodeService.consume(body.code().trim(), "game:" + body.player().toLowerCase());
        if (result.status() == BindCodeService.Status.LOCKED) {
            return badRequest("错误次数过多，请稍后再试");
        }
        if (result.status() == BindCodeService.Status.BAD_CODE) {
            return badRequest("验证码无效或已过期");
        }
        qqBindingService.bind(userOpt.get(), result.qq());
        LOG.info("[QQ绑定] {} 已绑定 QQ {}（游戏内确认）", userOpt.get().username(),
                QqBindingService.mask(result.qq()));
        return ResponseEntity.ok(cn.xmcraft.dreamport.server.web.ApiResponse.success("绑定成功",
                Map.of("qq", QqBindingService.mask(result.qq()))));
    }

    @PostMapping("/login-record")
    public ResponseEntity<Object> loginRecord(@RequestBody LoginRecordBody body,
                                              HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        minecraftVerifyService.recordLogin(body.name(), body.uuid(), body.ip());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public record EventBody(String type, String serverId, String player, String message) {
    }

    @PostMapping("/events")
    public ResponseEntity<Object> events(@RequestBody EventBody body, HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        switch (body.type() == null ? "" : body.type()) {
            case "chat" -> {
                chatService.broadcast("game", body.player() == null ? "?" : body.player(),
                        body.message() == null ? "" : body.message(), body.serverId());
                qqBridge.onGameChat(body.serverId(), body.player(), body.message());
            }
            case "join", "quit" -> {
                chatService.broadcast("system", "[系统]",
                        (body.player() == null ? "?" : body.player())
                                + ("join".equals(body.type()) ? " 加入了服务器" : " 离开了服务器"),
                        body.serverId());
                qqBridge.onGameEvent(body.serverId(), body.type(), body.player());
            }
            default -> {
                return badRequest("未知事件类型");
            }
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public record EconomySnapshotBody(List<EconomyService.PlayerEconomy> players) {
    }

    @PostMapping("/economy/snapshot")
    public ResponseEntity<Object> economySnapshot(@RequestBody EconomySnapshotBody body,
                                                  HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        economyService.saveSnapshot(body.players() == null ? List.of() : body.players());
        return ResponseEntity.ok(Map.of("ok", true, "count",
                body.players() == null ? 0 : body.players().size()));
    }

    // ---------- 积分任务/签到/邮件(插件通道,server-token) ----------

    /** 玩家退出时上报会话时长(驱动每日/每周在线任务) */
    @org.springframework.web.bind.annotation.PostMapping("/activity")
    public ResponseEntity<Object> activity(@org.springframework.web.bind.annotation.RequestBody ActivityBody body,
                                           HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (body == null || body.username() == null || body.username().isBlank()) {
            return badRequest("username 必填");
        }
        // 修复审计 H4：会话时长落 dp_daily_activity（此前 sessionSeconds 被丢弃，
        // 导致在线时长任务/成就永不推进）
        taskService.recordActivity(body.username(), body.sessionSeconds(), body.loginCount());
        taskService.onActivity(body.username());
        return ResponseEntity.ok(java.util.Map.of("success", true));
    }

    /** 游戏内签到上报(每日一次) */
    @org.springframework.web.bind.annotation.PostMapping("/signin")
    public ResponseEntity<Object> signin(@org.springframework.web.bind.annotation.RequestBody SigninBody body,
                                         HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (body == null || body.username() == null || body.username().isBlank()) {
            return badRequest("username 必填");
        }
        boolean first = taskService.signin(body.username(), "game");
        if (first) taskService.onSignin(body.username(), "game");
        return ResponseEntity.ok(java.util.Map.of("success", true, "first", first,
                "message", first ? "签到成功" : "今日已签到"));
    }

    /** 待领取奖励邮件 */
    @org.springframework.web.bind.annotation.GetMapping("/mail/pending")
    public ResponseEntity<Object> mailPending(@org.springframework.web.bind.annotation.RequestParam String username,
                                              HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (username == null || username.isBlank()) {
            return badRequest("username 必填");
        }
        return ResponseEntity.ok(java.util.Map.of("success", true,
                "data", java.util.Map.of("mails", rewardMailService.pending(username))));
    }

    /** 领取回执 */
    @org.springframework.web.bind.annotation.PostMapping("/mail/claimed")
    public ResponseEntity<Object> mailClaimed(@org.springframework.web.bind.annotation.RequestBody ClaimedBody body,
                                              HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (body == null) {
            return badRequest("请求体必填");
        }
        boolean ok = rewardMailService.markClaimed(body.id());
        return ResponseEntity.ok(java.util.Map.of("success", ok));
    }

    /** 当前佩戴称号(插件 PAPI 变量 %dreamport_title% 用);相对路径对应 Protocol.TITLE_ACTIVE */
    @org.springframework.web.bind.annotation.GetMapping("/title/active")
    public ResponseEntity<Object> titleActive(@org.springframework.web.bind.annotation.RequestParam String username,
                                              HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (username == null || username.isBlank()) {
            return badRequest("username 必填");
        }
        var t = titleService.activeTitle(username);
        return ResponseEntity.ok(java.util.Map.of("success", true,
                "code", t == null ? "" : t.code(),
                "name", t == null ? "" : t.name(),
                "color", t == null ? "" : cn.xmcraft.dreamport.server.titles.TitleService.effectiveGameColor(t)));
    }

    /** 玩家已拥有称号(/titles GUI 用);相对路径对应 Protocol.TITLE_MINE */
    @org.springframework.web.bind.annotation.GetMapping("/title/mine")
    public ResponseEntity<Object> titleMine(@org.springframework.web.bind.annotation.RequestParam String username,
                                            HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (username == null || username.isBlank()) {
            return badRequest("username 必填");
        }
        var defs = titleService.titles();
        List<java.util.Map<String, Object>> titles = new java.util.ArrayList<>();
        for (var row : titleService.ownedRows(username)) {
            String code = String.valueOf(row.get("title_code"));
            defs.stream().filter(d -> d.code().equals(code) && d.enabled()).findFirst()
                    .ifPresent(d -> titles.add(java.util.Map.of("code", d.code(),
                            "name", d.name(), "desc", d.desc(),
                            "color", cn.xmcraft.dreamport.server.titles.TitleService.effectiveGameColor(d))));
        }
        String active = titleService.activeTitleCode(username);
        return ResponseEntity.ok(java.util.Map.of("success", true,
                "active", active == null ? "" : active, "titles", titles));
    }

    /** 游戏内佩戴/脱下(code 为空 = 脱下);相对路径对应 Protocol.TITLE_EQUIP */
    @org.springframework.web.bind.annotation.PostMapping("/title/equip")
    public ResponseEntity<Object> titleEquip(@org.springframework.web.bind.annotation.RequestBody TitleEquipBody body,
                                             HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (body == null || body.username() == null || body.username().isBlank()) {
            return badRequest("username 必填");
        }
        try {
            if (body.code() == null || body.code().isBlank()) {
                titleService.unequip(body.username());
            } else {
                titleService.equip(body.username(), body.code());
            }
            return ResponseEntity.ok(java.util.Map.of("success", true));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ---------- 奖励礼包(服内采集,server-token) ----------

    public record KitSaveBody(String kitName, String player, String items, String summary) {
    }

    /** 服内管理员采集背包上传(/xmw kit save):存为礼包模板内容,状态置 ready */
    @org.springframework.web.bind.annotation.PostMapping("/kit/save")
    public ResponseEntity<Object> kitSave(@org.springframework.web.bind.annotation.RequestBody KitSaveBody body,
                                          HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        if (body == null || body.kitName() == null || body.kitName().isBlank()
                || body.player() == null || body.player().isBlank()) {
            return badRequest("kitName/player 必填");
        }
        var result = rewardKitService.saveCapture(body.kitName().trim(), body.player(),
                body.items(), body.summary());
        return result.success()
                ? ResponseEntity.ok(java.util.Map.of("success", true, "message", result.message()))
                : ResponseEntity.ok(java.util.Map.of("success", false, "message", result.message()));
    }

    /** 礼包模板列表(/xmw kit list 用) */
    @org.springframework.web.bind.annotation.GetMapping("/kit/list")
    public ResponseEntity<Object> kitList(HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        return ResponseEntity.ok(java.util.Map.of("success", true, "kits", rewardKitService.list()));
    }

    public record TitleEquipBody(String username, String code) {
    }

    public record ActivityBody(String username, long sessionSeconds, int loginCount) {
    }

    public record SigninBody(String username) {
    }

    public record ClaimedBody(long id) {
    }

    @GetMapping("/commands/whitelist")
    public ResponseEntity<Object> whitelistCommands(HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        String serverId = request.getParameter("serverId");
        return ResponseEntity.ok(Map.of("commands",
                minecraftVerifyService.drainWhitelistCommands(serverId == null ? "main" : serverId)));
    }

    /** 游戏收件箱轮询：网页/QQ 消息下行进服（插件定时拉取后 broadcastMessage，docs/ASTRBOT_PLAN.md §5.3） */
    @GetMapping("/messages/pending")
    public ResponseEntity<Object> pendingMessages(HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        long since;
        try {
            since = Long.parseLong(request.getParameter("since"));
        } catch (NumberFormatException e) {
            since = 0;
        }
        var items = qqBridge.gameInboxSince(since);
        List<Map<String, Object>> messages = items.stream()
                .map(i -> Map.<String, Object>of("seq", i.seq(), "text", i.payload()))
                .toList();
        return ResponseEntity.ok(Map.of("messages", messages, "latest", qqBridge.gameInboxLatest()));
    }

    // ---------- 游戏内管理命令（/xmw 经服务器令牌调用，替代旧版进程内直调） ----------

    public record AdminOpBody(String username, String reason, Integer days) {
    }

    @PostMapping("/admin-ops/{action}")
    public ResponseEntity<Object> adminOp(@PathVariable String action,
                                          @RequestBody AdminOpBody body,
                                          HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        String operator = "console@" + request.getHeader(
                cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_ID);
        var result = switch (action == null ? "" : action) {
            case "approve" -> reviewService.approve(body.username(), operator, "zh");
            case "reject" -> reviewService.reject(body.username(), operator,
                    body.reason() == null ? "未通过审核" : body.reason(), "zh");
            case "ban" -> reviewService.ban(body.username(), operator,
                    body.reason() == null ? "违规操作" : body.reason(), body.days());
            case "unban" -> reviewService.unban(body.username(), operator);
            case "delete" -> reviewService.delete(body.username(), operator);
            default -> null;
        };
        if (result == null) {
            return badRequest("未知操作: " + action);
        }
        return result.success()
                ? ResponseEntity.ok(Map.of("ok", true, "message", result.message()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "message", result.message()));
    }

    @GetMapping("/admin-ops/list")
    public ResponseEntity<Object> adminList(HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        return ResponseEntity.ok(userService.pendingUsers());
    }

    @GetMapping("/admin-ops/info/{username}")
    public ResponseEntity<Object> adminInfo(@PathVariable String username,
                                            HttpServletRequest request) {
        ResponseEntity<Object> auth = requireServerToken(request);
        if (auth != null) {
            return auth;
        }
        return ResponseEntity.ok(userService.userInfo(username));
    }

    /**
     * 服务器通道鉴权：
     * - shared 模式（默认）：全局 wl.internal.server-token 单令牌
     * - per_server 模式：按 X-Server-Id 查 dp_server.token_hash（SHA-256）比对且 enabled=TRUE；
     *   全局令牌保留为应急通道（break-glass，命中记 warn 便于审计）
     */
    private ResponseEntity<Object> requireServerToken(HttpServletRequest request) {
        String token = request.getHeader(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_TOKEN);
        if (token == null || token.isBlank()) {
            return unauthorized();
        }
        String global = props.internal().serverToken();
        if (global != null && !global.isBlank() && global.equals(token)) {
            if ("per_server".equals(tokenMode())) {
                LOG.warn("[鉴权] {} 使用全局共享令牌（per_server 模式应急通道）",
                        request.getHeader(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_ID));
            }
            return null;
        }
        if (!"per_server".equals(tokenMode())) {
            return unauthorized();
        }
        String serverId = request.getHeader(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_ID);
        if (serverId == null || serverId.isBlank()) {
            return unauthorized();
        }
        String expected = statsService.tokenHashOf(serverId);
        if (expected == null || !sha256Matches(token, expected)) {
            return unauthorized();
        }
        return null;
    }

    private String tokenMode() {
        var cfg = settingService.get(SettingService.KEY_SECURITY_CONFIG, java.util.Map.class);
        Object mode = cfg == null ? null : cfg.get("tokenMode");
        return mode == null ? "shared" : String.valueOf(mode);
    }

    /** 恒时比较 SHA-256(令牌) 与库中哈希 */
    private static boolean sha256Matches(String token, String expectedHex) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return java.security.MessageDigest.isEqual(
                    hex.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    expectedHex.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", ErrorCode.TOKEN_MISSING.code(), "message", "服务器认证失败"));
    }

    private ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", 2001, "message", message));
    }
}
