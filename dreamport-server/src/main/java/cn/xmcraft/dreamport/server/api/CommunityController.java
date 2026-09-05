package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.appeal.AppealRecord;
import cn.xmcraft.dreamport.server.appeal.AppealRepository;
import cn.xmcraft.dreamport.server.config.WlProps;
import cn.xmcraft.dreamport.server.invite.InviteRecord;
import cn.xmcraft.dreamport.server.invite.InviteRepository;
import cn.xmcraft.dreamport.server.invite.InviteService;
import cn.xmcraft.dreamport.server.machine.PublicMachineRecord;
import cn.xmcraft.dreamport.server.machine.PublicMachineRepository;
import cn.xmcraft.dreamport.server.notification.NotificationRecord;
import cn.xmcraft.dreamport.server.notification.NotificationRepository;
import cn.xmcraft.dreamport.server.review.ReviewService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.village.VillageTradeRecord;
import cn.xmcraft.dreamport.server.village.VillageTradeRepository;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社区端点（契约对齐旧版）：村民族谱 /village、公共机器 /machine、玩家目录 /players、
 * 邀请 /invite、通知 /notifications、问卷申诉 /questionnaire/appeal。
 */
@RestController
@RequestMapping("/api")
public class CommunityController {

    private static final Set<String> WORLDS = Set.of("survival", "factory", "resource");
    private static final Set<String> MACHINE_TYPES = Set.of("红石机器", "刷怪塔", "农场", "自动化设备", "交通系统", "其他");

    private final VillageTradeRepository villageRepository;
    private final PublicMachineRepository machineRepository;
    private final InviteRepository inviteRepository;
    private final InviteService inviteService;
    private final NotificationRepository notificationRepository;
    private final AppealRepository appealRepository;
    private final UserRepository userRepository;
    private final ReviewService reviewService;
    private final WlProps props;
    private final SettingService settingService;

    public CommunityController(VillageTradeRepository villageRepository,
                               PublicMachineRepository machineRepository,
                               InviteRepository inviteRepository, InviteService inviteService,
                               NotificationRepository notificationRepository,
                               AppealRepository appealRepository, UserRepository userRepository,
                               ReviewService reviewService, WlProps props, SettingService settingService) {
        this.villageRepository = villageRepository;
        this.machineRepository = machineRepository;
        this.inviteRepository = inviteRepository;
        this.inviteService = inviteService;
        this.notificationRepository = notificationRepository;
        this.appealRepository = appealRepository;
        this.userRepository = userRepository;
        this.reviewService = reviewService;
        this.props = props;
        this.settingService = settingService;
    }

    // ---------- 村民族谱 ----------

    public record VillageBody(String world, Integer x, Integer y, Integer z,
                              String itemInput, String itemOutput, Double price) {
    }

    @GetMapping("/village/list")
    public ResponseEntity<Object> villageList(@RequestParam(required = false) String world) {
        var list = villageRepository.findByStatusOrderByCreatedAtDesc("approved").stream()
                .filter(t -> world == null || world.isBlank() || world.equals(t.world()))
                .toList();
        return ResponseEntity.ok(Map.of("villages", list));
    }

    @PostMapping("/village/submit")
    public ResponseEntity<Object> villageSubmit(@RequestBody VillageBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        if (body.world() == null || !WORLDS.contains(body.world()) || body.x() == null || body.y() == null
                || body.z() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("世界与坐标必填"));
        }
        villageRepository.save(new VillageTradeRecord(null, me, body.world(), body.x(), body.y(),
                body.z(), body.itemInput(), body.itemOutput(), body.price(), null, null, null, null));
        return ResponseEntity.ok(ApiResponse.success("已提交，等待管理员审核"));
    }

    @GetMapping("/village/admin/pending")
    public ResponseEntity<Object> villagePending(HttpServletRequest request) {
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        return ResponseEntity.ok(Map.of("pending", villageRepository.findByStatusOrderByCreatedAtDesc("pending")));
    }

    @PostMapping("/village/admin/{action}/{id}")
    public ResponseEntity<Object> villageReview(@PathVariable String action, @PathVariable Long id,
                                                HttpServletRequest request) {
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        if (!Set.of("approve", "reject").contains(action)) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("未知操作"));
        }
        var trade = villageRepository.findById(id);
        if (trade.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("记录不存在"));
        }
        VillageTradeRecord t = trade.get();
        villageRepository.save(new VillageTradeRecord(t.id(), t.playerName(), t.world(), t.x(), t.y(),
                t.z(), t.itemInput(), t.itemOutput(), t.price(), "approve".equals(action) ? "approved" : "rejected",
                t.createdAt(), AuthUtil.currentUser(request), System.currentTimeMillis()));
        return ResponseEntity.ok(ApiResponse.success("已" + ("approve".equals(action) ? "通过" : "拒绝")));
    }

    // ---------- 公共机器 ----------

    public record MachineBody(String name, String type, String world, Integer x, Integer y, Integer z,
                              String builder, String usage) {
    }

    @GetMapping("/machine/list")
    public ResponseEntity<Object> machineList(@RequestParam(required = false) String world,
                                              @RequestParam(required = false) String type) {
        var list = machineRepository.findByStatusOrderByCreatedAtDesc("approved").stream()
                .filter(m -> world == null || world.isBlank() || world.equals(m.world()))
                .filter(m -> type == null || type.isBlank() || type.equals(m.type()))
                .toList();
        return ResponseEntity.ok(Map.of("machines", list));
    }

    @PostMapping("/machine/submit")
    public ResponseEntity<Object> machineSubmit(@RequestBody MachineBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        if (body.name() == null || body.name().isBlank() || body.world() == null
                || !WORLDS.contains(body.world()) || body.x() == null || body.y() == null || body.z() == null
                || body.usage() == null || body.usage().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("名称/世界/坐标/用途必填"));
        }
        machineRepository.save(new PublicMachineRecord(null, body.name(), body.type(), body.world(),
                body.x(), body.y(), body.z(),
                body.builder() == null || body.builder().isBlank() ? me : body.builder(),
                body.usage(), null, null, me, null, null, null));
        return ResponseEntity.ok(ApiResponse.success("已提交，等待管理员审核"));
    }

    @PostMapping("/machine/upload")
    public ResponseEntity<Object> machineUpload(@RequestParam("file") MultipartFile file,
                                                HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("文件为空"));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("截图不能超过 5MB"));
        }
        String ext = detectImageExt(file.getOriginalFilename());
        if (ext == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("仅支持 jpg/png/gif/webp"));
        }
        String filename = java.util.UUID.randomUUID() + ext;
        try {
            Path dir = Path.of("static", "uploads");
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toAbsolutePath());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.failure("上传失败"));
        }
        return ResponseEntity.ok(ApiResponse.success("上传成功",
                Map.of("url", "/uploads/" + filename, "filename", filename)));
    }

    private String detectImageExt(String filename) {
        if (filename == null) {
            return null;
        }
        String lower = filename.toLowerCase();
        for (String ext : List.of(".jpg", ".png", ".gif", ".webp")) {
            if (lower.endsWith(ext)) {
                return ext;
            }
        }
        return null;
    }

    @GetMapping("/machine/admin/pending")
    public ResponseEntity<Object> machinePending(HttpServletRequest request) {
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        return ResponseEntity.ok(Map.of("pending", machineRepository.findByStatusOrderByCreatedAtDesc("pending")));
    }

    @PostMapping("/machine/admin/{action}/{id}")
    public ResponseEntity<Object> machineReview(@PathVariable String action, @PathVariable Long id,
                                                HttpServletRequest request) {
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        var machine = machineRepository.findById(id);
        if (machine.isEmpty() || !Set.of("approve", "reject").contains(action)) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("记录不存在或未知操作"));
        }
        PublicMachineRecord m = machine.get();
        machineRepository.save(new PublicMachineRecord(m.id(), m.name(), m.type(), m.world(), m.x(), m.y(),
                m.z(), m.builder(), m.usageText(), m.screenshotUrl(),
                "approve".equals(action) ? "approved" : "rejected", m.submitter(), m.createdAt(),
                AuthUtil.currentUser(request), System.currentTimeMillis()));
        return ResponseEntity.ok(ApiResponse.success("已处理"));
    }

    // ---------- 玩家目录 ----------

    @GetMapping("/players/list")
    public ResponseEntity<Object> playersList() {
        // 全部 approved/封禁 用户均展示；MC 名缺失时回退用户名（修复玩家目录为空）
        List<Map<String, Object>> players = userRepository.listAll().stream()
                .filter(u -> "approved".equals(u.status()) || "banned".equals(u.status()))
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.username(), b.username()))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("username", u.username());
                    m.put("minecraftName", u.minecraftName() == null ? u.username() : u.minecraftName());
                    m.put("uuid", u.minecraftUuid());
                    m.put("status", u.status());
                    m.put("regTime", u.regTime());
                    return m;
                }).toList();
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("list", players)));
    }

    @GetMapping("/players/profile/{username}")
    public ResponseEntity<Object> playerProfile(@PathVariable String username) {
        var userOpt = userRepository.findByUsernameIgnoreCase(username).or(() ->
                userRepository.listAll().stream()
                        .filter(u -> username.equalsIgnoreCase(u.minecraftName())).findFirst());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("玩家不存在"));
        }
        UserRecord u = userOpt.get();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("username", u.username());
        profile.put("minecraftName", u.minecraftName());
        profile.put("status", u.status());
        profile.put("regTime", u.regTime());
        profile.put("daysSinceReg", (System.currentTimeMillis() - u.regTime()) / 86_400_000L);
        profile.put("qqNumber", u.qqNumber());
        return ResponseEntity.ok(profile);
    }

    // ---------- 邀请 ----------

    @PostMapping("/invite/generate")
    public ResponseEntity<Object> inviteGenerate(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        return wrap(inviteService.generate(me));
    }

    @GetMapping("/invite/my-codes")
    public ResponseEntity<Object> myCodes(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        List<Map<String, Object>> codes = new ArrayList<>();
        for (InviteRecord i : inviteService.myCodes(me)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", i.code());
            m.put("status", i.status());
            m.put("invitee", i.inviteeUsername());
            m.put("createdAt", i.createdAt());
            m.put("expiresAt", i.expiresAt());
            m.put("usedAt", i.usedAt());
            codes.add(m);
        }
        return ResponseEntity.ok(Map.of("codes", codes));
    }

    @GetMapping("/invite/pending")
    public ResponseEntity<Object> invitePending(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(Map.of("pending", inviteService.pendingFor(me)));
    }

    @PostMapping("/invite/apply")
    public ResponseEntity<Object> inviteApply(@RequestBody Map<String, String> body,
                                              HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        return wrap(inviteService.apply(me, body.get("code")));
    }

    @PostMapping("/invite/confirm")
    public ResponseEntity<Object> inviteConfirm(@RequestBody Map<String, String> body,
                                                HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        return wrap(inviteService.confirm(me, body.get("username")));
    }

    @PostMapping("/invite/reject")
    public ResponseEntity<Object> inviteReject(@RequestBody Map<String, String> body,
                                               HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        return wrap(inviteService.reject(me, body.get("username")));
    }

    // ---------- 通知 ----------

    @GetMapping("/notifications")
    public ResponseEntity<Object> notifications(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        var list = notificationRepository.findTop50ByUsernameIgnoreCaseOrderByCreatedAtDesc(me);
        long unread = notificationRepository.findByUsernameIgnoreCaseAndIsReadFalse(me).size();
        return ResponseEntity.ok(Map.of("notifications", list, "unreadCount", unread));
    }

    public record NotificationReadBody(Long id) {
    }

    @PostMapping("/notifications/read")
    public ResponseEntity<Object> notificationRead(@RequestBody NotificationReadBody body,
                                                   HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        notificationRepository.findById(body.id() == null ? -1 : body.id())
                .filter(n -> me.equalsIgnoreCase(n.username()))
                .map(NotificationRecord::markRead)
                .ifPresent(notificationRepository::save);
        return ResponseEntity.ok(ApiResponse.success("已读"));
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Object> notificationReadAll(HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        notificationRepository.findByUsernameIgnoreCaseAndIsReadFalse(me)
                .forEach(n -> notificationRepository.save(n.markRead()));
        return ResponseEntity.ok(ApiResponse.success("全部已读"));
    }

    // ---------- 申诉 ----------

    public record AppealSubmitBody(String reason) {
    }

    @PostMapping("/questionnaire/appeal")
    public ResponseEntity<Object> submitAppeal(@RequestBody AppealSubmitBody body,
                                               HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return unauthorized();
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(me);
        if (userOpt.isEmpty() || !"rejected".equals(userOpt.get().status())) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("仅被拒绝的用户可以申诉"));
        }
        if (body.reason() == null || body.reason().length() < 10) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("申诉理由至少 10 字"));
        }
        if (appealRepository.existsByUsernameIgnoreCaseAndStatus(me, "pending")) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("已有待处理的申诉"));
        }
        appealRepository.save(new AppealRecord(null, me, body.reason(), null, null, null, null, null));
        return ResponseEntity.ok(ApiResponse.success("申诉已提交，等待管理员处理"));
    }

    @GetMapping("/admin/appeals")
    public ResponseEntity<Object> allAppeals(HttpServletRequest request) {
        if (!isAdminOfSite(request)) {
            return forbidden();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", appealRepository.findAllByOrderByCreatedAtDesc());
        return ResponseEntity.ok(body);
    }

    // ---------- 工具 ----------

    private boolean isAdminOfSite(HttpServletRequest request) {
        // 村谱/机器审核沿用旧版 config.admins 名单语义（存 dp_setting）
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return false;
        }
        if (AuthUtil.isAdmin(request)) {
            return true;
        }
        List<String> admins = settingService.get(SettingService.KEY_ADMINS, List.class);
        return admins != null && admins.stream().anyMatch(a -> String.valueOf(a).equalsIgnoreCase(me));
    }

    private ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
    }

    private ResponseEntity<Object> wrap(InviteService.Result result) {
        return result.success()
                ? ResponseEntity.ok(ApiResponse.success(result.message()))
                : ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
    }
}
