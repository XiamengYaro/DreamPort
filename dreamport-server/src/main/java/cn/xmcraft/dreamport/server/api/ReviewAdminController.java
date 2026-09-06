package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.appeal.AppealRepository;
import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.machine.PublicMachineRepository;
import cn.xmcraft.dreamport.server.review.ReviewService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import cn.xmcraft.dreamport.server.village.VillageTradeRepository;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端：用户管理 / 审核 / 审计 / 维护模式 / 申诉与各审核队列
 * （契约对齐旧版 AdminUserHandler/AppealHandler/AdminAuditHandler/MaintenanceHandler）。
 */
@RestController
@RequestMapping("/api/admin")
public class ReviewAdminController {

    private final UserRepository userRepository;
    private final ReviewService reviewService;
    private final AuditService auditService;
    private final AppealRepository appealRepository;
    private final VillageTradeRepository villageTradeRepository;
    private final PublicMachineRepository machineRepository;
    private final SettingService settingService;

    public ReviewAdminController(UserRepository userRepository, ReviewService reviewService,
                                 AuditService auditService, AppealRepository appealRepository,
                                 VillageTradeRepository villageTradeRepository,
                                 PublicMachineRepository machineRepository,
                                 SettingService settingService) {
        this.userRepository = userRepository;
        this.reviewService = reviewService;
        this.auditService = auditService;
        this.appealRepository = appealRepository;
        this.villageTradeRepository = villageTradeRepository;
        this.machineRepository = machineRepository;
        this.settingService = settingService;
    }

    // ---------- 请求体 ----------

    public record UsernameBody(String username, String reason, String language, String operator, Integer days) {
    }

    public record AddUserBody(String username, String email, String status) {
    }

    public record UpdateUserBody(String username, String email, String status) {
    }

    public record StatusBody(String username, String status) {
    }

    public record BatchBody(List<String> usernames, String reason) {
    }

    public record AppealBody(Long id, String reply) {
    }

    public record MaintenanceBody(Boolean enabled) {
    }

    public record QuestionnaireUpdateBody(String username, Integer questionnaireScore,
                                          Boolean questionnairePassed, String questionnaireReasons,
                                          String status) {
    }

    // ---------- 用户管理 ----------

    @GetMapping("/users")
    public ResponseEntity<Object> users(HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        List<Map<String, Object>> users = userRepository.listAll().stream()
                .map(this::toUserMap).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", Map.of("users", users));
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> toUserMap(UserRecord u) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", u.username());
        map.put("email", u.email());
        map.put("status", u.status());
        map.put("regTime", u.regTime());
        map.put("questionnaireScore", u.questionnaireScore());
        map.put("questionnairePassed", u.questionnairePassed());
        map.put("questionnaireReasons", u.questionnaireReasons());
        map.put("minecraftName", u.minecraftName());
        map.put("minecraftUuid", u.minecraftUuid());
        map.put("bedrockName", u.bedrockName());
        map.put("bedrockVerified", u.bedrockVerified());
        map.put("banReason", u.banReason());
        map.put("banTime", u.banTime());
        map.put("avatar", u.avatar());
        map.put("invitedBy", u.invitedBy());
        map.put("qqNumber", u.qqNumber());
        return map;
    }

    @PostMapping("/user/approve")
    public ResponseEntity<Object> approve(@RequestBody UsernameBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        var result = reviewService.approve(body.username(), operator(request), body.language());
        return wrap(result);
    }

    @PostMapping("/user/reject")
    public ResponseEntity<Object> reject(@RequestBody UsernameBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        return wrap(reviewService.reject(body.username(), operator(request), body.reason(),
                body.language()));
    }

    @PostMapping("/user/ban")
    public ResponseEntity<Object> ban(@RequestBody UsernameBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        return wrap(reviewService.ban(body.username(), operator(request), body.reason(), body.days()));
    }

    @PostMapping("/user/unban")
    public ResponseEntity<Object> unban(@RequestBody UsernameBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        return wrap(reviewService.unban(body.username(), operator(request)));
    }

    @PostMapping("/user/delete")
    public ResponseEntity<Object> delete(@RequestBody UsernameBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        return wrap(reviewService.delete(body.username(), operator(request)));
    }

    @PostMapping("/user/add")
    public ResponseEntity<Object> add(@RequestBody AddUserBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        return wrap(reviewService.add(body.username(), body.email(), body.status(), operator(request)));
    }

    @PostMapping("/user/update")
    public ResponseEntity<Object> update(@RequestBody UpdateUserBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(body.username());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("用户不存在"));
        }
        UserRecord user = userOpt.get();
        UserRecord updated = new UserRecord(user.id(), user.username(),
                body.email() != null ? body.email() : user.email(),
                body.status() != null ? body.status() : user.status(),
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), user.banReason(), user.banTime(), user.banUntil(), user.avatar());
        userRepository.save(updated);
        auditService.log("update_user", operator(request), body.username(), null);
        return ResponseEntity.ok(ApiResponse.success("已更新用户 " + body.username()));
    }

    @PostMapping("/user/update-status")
    public ResponseEntity<Object> updateStatus(@RequestBody StatusBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        return wrap(reviewService.forceStatus(body.username(), operator(request), body.status()));
    }

    @PostMapping({"/user/batch-approve", "/user/batch-reject", "/user/batch-ban", "/user/batch-delete"})
    public ResponseEntity<Object> batch(@RequestBody BatchBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        String path = request.getRequestURI();
        int success = 0;
        int failed = 0;
        String op = operator(request);
        for (String username : body.usernames() == null ? List.<String>of() : body.usernames()) {
            var result = path.endsWith("batch-approve") ? reviewService.approve(username, op, "zh")
                    : path.endsWith("batch-reject") ? reviewService.reject(username, op, body.reason(), "zh")
                    : path.endsWith("batch-ban") ? reviewService.ban(username, op, body.reason())
                    : reviewService.delete(username, op);
            if (result.success()) {
                success++;
            } else {
                failed++;
            }
        }
        auditService.log("batch", op, success + "/" + (success + failed), path);
        return ResponseEntity.ok(ApiResponse.success("批量操作完成",
                Map.of("success", success, "failed", failed)));
    }

    // ---------- 审核/申诉/机器/村谱 待审队列 ----------

    @GetMapping("/verify")
    public ResponseEntity<Object> adminVerify(@RequestBody(required = false) Map<String, Object> body,
                                              HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", me != null);
        data.put("username", me);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/pending")
    public ResponseEntity<Object> pending(HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pendingUsers", userRepository.listAll().stream()
                .filter(u -> "pending_review".equals(u.status()) || "pending".equals(u.status()))
                .map(this::toUserMap).toList());
        data.put("appeals", appealRepository.findByStatusOrderByCreatedAtDesc("pending"));
        data.put("villageTrades", villageTradeRepository.findByStatusOrderByCreatedAtDesc("pending"));
        data.put("machines", machineRepository.findByStatusOrderByCreatedAtDesc("pending"));
        return ResponseEntity.ok(data);
    }

    @PostMapping("/appeals/approve")
    public ResponseEntity<Object> approveAppeal(@RequestBody AppealBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        // FIX(legacy)：通过申诉后若用户仍 rejected → 转人工复核队列 pending_review（对齐旧行为）
        var appealOpt = appealRepository.findById(body.id() == null ? -1 : body.id());
        if (appealOpt.isEmpty() || !"pending".equals(appealOpt.get().status())) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("申诉不存在或已处理"));
        }
        var appeal = appealOpt.get();
        var userOpt = userRepository.findByUsernameIgnoreCase(appeal.username());
        userOpt.filter(u -> "rejected".equals(u.status()))
                .ifPresent(u -> reviewService.forceStatus(u.username(), operator(request), "pending_review"));
        auditService.log("appeal_approved", operator(request), appeal.username(), body.reply());
        return ResponseEntity.ok(ApiResponse.success("申诉已通过"));
    }

    @PostMapping("/appeals/reject")
    public ResponseEntity<Object> rejectAppeal(@RequestBody AppealBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        var appealOpt = appealRepository.findById(body.id() == null ? -1 : body.id());
        if (appealOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("申诉不存在"));
        }
        auditService.log("appeal_rejected", operator(request), appealOpt.get().username(), body.reply());
        return ResponseEntity.ok(ApiResponse.success("申诉已拒绝"));
    }

    @PostMapping("/user/update-questionnaire")
    public ResponseEntity<Object> updateQuestionnaire(@RequestBody QuestionnaireUpdateBody body,
                                                      HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(body.username());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("用户不存在"));
        }
        UserRecord user = userOpt.get();
        UserRecord updated = new UserRecord(user.id(), user.username(), user.email(),
                body.status() != null ? body.status() : user.status(),
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(),
                body.questionnaireScore() != null ? body.questionnaireScore() : user.questionnaireScore(),
                body.questionnairePassed() != null ? body.questionnairePassed() : user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(),
                body.questionnaireReasons() != null ? body.questionnaireReasons() : user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(),
                user.microsoftVerified(), user.verifiedAt(), user.verifyType(), user.invitedBy(),
                user.bedrockUuid(), user.bedrockName(), user.bedrockVerified(), user.bedrockVerifiedAt(),
                user.banReason(), user.banTime(), user.banUntil(), user.avatar());
        userRepository.save(updated);
        auditService.log("admin_update_questionnaire", operator(request), body.username(), null);
        return ResponseEntity.ok(ApiResponse.success("问卷成绩已更新"));
    }

    // ---------- 审计 / 维护模式 ----------

    @GetMapping("/audits")
    public ResponseEntity<Object> audits(HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        // 前端期望 data 直接为数组
        List<Object> audits = new ArrayList<>(auditService.recent());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", audits);
        return ResponseEntity.ok(body);
    }

    /** 管理端统计概览（字段对齐前端 statsOverview.*） */
    public record BedrockBody(String username, String bedrockName) {
    }

    /** 代管基岩 ID 绑定（自动加 Geyser 前缀，重置验证态） */
    @PostMapping("/user/set-bedrock")
    public ResponseEntity<Object> setBedrock(@RequestBody BedrockBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(body.username() == null ? "" : body.username());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("用户不存在"));
        }
        var u = userOpt.get();
        String bn = body.bedrockName() == null || body.bedrockName().isBlank() ? ""
                : (body.bedrockName().startsWith(".") ? body.bedrockName() : "." + body.bedrockName());
        UserRecord updated = new UserRecord(u.id(), u.username(), u.email(), u.status(),
                u.passwordAlgo(), u.passwordHash(), u.regTime(), u.discordId(), u.qqNumber(), u.qqBoundAt(),
                u.questionnaireScore(), u.questionnairePassed(), u.questionnaireReviewSummary(),
                u.questionnaireScoredAt(), u.questionnaireReasons(), u.questionnaireAnswers(),
                u.minecraftUuid(), u.minecraftName(), u.microsoftVerified(), u.verifiedAt(), u.verifyType(),
                u.invitedBy(), null, bn, false, null, u.banReason(), u.banTime(), u.banUntil(), u.avatar());
        userRepository.save(updated);
        auditService.log("set_bedrock", operator(request), u.username(), bn);
        return ResponseEntity.ok(ApiResponse.success("基岩 ID 已设置"));
    }

    /** 代管基岩验证：使用该用户最近一条进服记录直接验证 */
    @PostMapping("/user/verify-bedrock")
    public ResponseEntity<Object> verifyBedrock(@RequestBody BedrockBody body, HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(body.username() == null ? "" : body.username());
        if (userOpt.isEmpty() || userOpt.get().bedrockName() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("该用户未绑定基岩 ID"));
        }
        var u = userOpt.get();
        UserRecord updated = new UserRecord(u.id(), u.username(), u.email(), "approved",
                u.passwordAlgo(), u.passwordHash(), u.regTime(), u.discordId(), u.qqNumber(), u.qqBoundAt(),
                u.questionnaireScore(), u.questionnairePassed(), u.questionnaireReviewSummary(),
                u.questionnaireScoredAt(), u.questionnaireReasons(), u.questionnaireAnswers(),
                u.minecraftUuid(), u.minecraftName(), u.microsoftVerified(), u.verifiedAt(), u.verifyType(),
                u.invitedBy(), "admin-verified", u.bedrockName(), true, System.currentTimeMillis(),
                u.banReason(), u.banTime(), u.banUntil(), u.avatar());
        userRepository.save(updated);
        auditService.log("verify_bedrock", operator(request), u.username(), u.bedrockName());
        return ResponseEntity.ok(ApiResponse.success("基岩 ID 已验证"));
    }

    /** 近 30 天注册趋势 */
    @GetMapping("/stats/registrations")
    public ResponseEntity<Object> statsRegistrations(HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        var all = userRepository.listAll();
        List<Map<String, Object>> trend = new java.util.ArrayList<>();
        long todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000L);
        for (int i = 29; i >= 0; i--) {
            long dayStart = todayStart - (long) i * 86_400_000L;
            long dayEnd = dayStart + 86_400_000L;
            long c = all.stream().filter(u -> u.regTime() != null && u.regTime() >= dayStart && u.regTime() < dayEnd).count();
            trend.add(Map.of("date", new java.text.SimpleDateFormat("MM-dd").format(new java.util.Date(dayStart)),
                    "count", c));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", Map.of("trend", trend));
        return ResponseEntity.ok(body);
    }

    /** 问卷统计分布 */
    @GetMapping("/stats/questionnaires")
    public ResponseEntity<Object> statsQuestionnaires(HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        var scored = userRepository.listAll().stream()
                .filter(u -> u.questionnaireScoredAt() != null && u.questionnaireScoredAt() > 0).toList();
        long passed = scored.stream().filter(u -> Boolean.TRUE.equals(u.questionnairePassed())).count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", scored.size());
        data.put("passed", passed);
        data.put("failed", scored.size() - passed);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/stats/overview")
    public ResponseEntity<Object> statsOverview(HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        var all = userRepository.listAll();
        long approved = all.stream().filter(u -> "approved".equals(u.status())).count();
        long pending = all.stream().filter(u -> "pending".equals(u.status()) || "pending_review".equals(u.status())).count();
        long banned = all.stream().filter(u -> "banned".equals(u.status())).count();
        long scored = all.stream().filter(u -> u.questionnaireScoredAt() != null && u.questionnaireScoredAt() > 0).toList().size();
        long passed = all.stream().filter(u -> Boolean.TRUE.equals(u.questionnairePassed())).count();
        long failed = scored - passed;
        long now = System.currentTimeMillis();
        long todayStart = now - (now % 86_400_000L);
        long week = todayStart - 6L * 86_400_000L;
        long month = todayStart - 29L * 86_400_000L;
        long todayReg = all.stream().filter(u -> u.regTime() != null && u.regTime() >= todayStart).count();
        long weekReg = all.stream().filter(u -> u.regTime() != null && u.regTime() >= week).count();
        long monthReg = all.stream().filter(u -> u.regTime() != null && u.regTime() >= month).count();
        double avg = all.stream().filter(u -> u.questionnaireScoredAt() != null && u.questionnaireScoredAt() > 0)
                .mapToInt(UserRecord::questionnaireScore).average().orElse(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", all.size());
        data.put("approvedUsers", approved);
        data.put("pendingUsers", pending);
        data.put("bannedUsers", banned);
        data.put("todayRegistrations", todayReg);
        data.put("weekRegistrations", weekReg);
        data.put("monthRegistrations", monthReg);
        data.put("averageScore", Math.round(avg * 10) / 10.0);
        data.put("questionnairePassed", passed);
        data.put("questionnaireFailed", failed);
        data.put("questionnairePassRate", scored == 0 ? 0 : Math.round(passed * 1000.0 / scored) / 10.0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    /** 问卷成绩详情（前端 viewQuestionnaire 读 data.questionnaireScore 等） */
    @GetMapping("/questionnaire/detail/{username}")
    public ResponseEntity<Object> questionnaireDetail(@PathVariable String username,
                                                      HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("用户不存在"));
        }
        var u = userOpt.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", u.username());
        data.put("questionnaireScore", u.questionnaireScore());
        data.put("questionnairePassed", u.questionnairePassed());
        data.put("questionnaireReasons", u.questionnaireReasons());
        data.put("questionnaireAnswers", u.questionnaireAnswers());
        data.put("questionnaireReviewSummary", u.questionnaireReviewSummary());
        data.put("questionnaireScoredAt", u.questionnaireScoredAt());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/maintenance")
    public ResponseEntity<Object> getMaintenance(HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(Map.of("enabled", settingService
                .getBool(SettingService.KEY_MAINTENANCE, false)));
    }

    @PostMapping("/maintenance")
    public ResponseEntity<Object> setMaintenance(@RequestBody MaintenanceBody body,
                                                 HttpServletRequest request) {
        ResponseEntity<Object> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        boolean enabled = Boolean.TRUE.equals(body.enabled());
        settingService.set(SettingService.KEY_MAINTENANCE, enabled);
        auditService.log("maintenance", operator(request), String.valueOf(enabled), null);
        return ResponseEntity.ok(ApiResponse.success("维护模式已" + (enabled ? "开启" : "关闭")));
    }

    // ---------- 工具 ----------

    private ResponseEntity<Object> requireAdmin(HttpServletRequest request) {
        if (AuthUtil.currentUser(request) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.failure("未登录"));
        }
        if (!AuthUtil.isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure("需要管理员权限"));
        }
        return null;
    }

    private String operator(HttpServletRequest request) {
        return AuthUtil.currentUser(request) == null ? "system" : AuthUtil.currentUser(request);
    }

    private ResponseEntity<Object> wrap(ReviewService.Result result) {
        return result.success()
                ? ResponseEntity.ok(ApiResponse.success(result.message()))
                : ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
    }
}
