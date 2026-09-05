package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.chat.ChatService;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.stats.ServerStatsService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AstrBot 机器人端点（契约对齐旧版 /api/astrbot/*，X-API-Token 鉴权）：
 * QQ↔MC 绑定查询/绑定/解绑、服务器状态、玩家列表、消息进服广播。
 */
@RestController
@RequestMapping("/api/astrbot")
public class AstrBotController {

    private final UserRepository userRepository;
    private final ChatService chatService;
    private final ServerStatsService statsService;
    private final SettingService settingService;

    public AstrBotController(UserRepository userRepository, ChatService chatService,
                             ServerStatsService statsService, SettingService settingService) {
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.statsService = statsService;
        this.settingService = settingService;
    }

    public record BindBody(String qq, String minecraftName) {
    }

    public record ChatBody(String sender, String message) {
    }

    private boolean authorized(HttpServletRequest request) {
        String token = request.getHeader("X-API-Token");
        String expected = settingService.get("astrbot.api_token", String.class);
        return token != null && expected != null && !expected.isBlank()
                && token.replace("\"", "").equals(expected.replace("\"", ""));
    }

    private ResponseEntity<Object> denied() {
        return ResponseEntity.status(401).body(ApiResponse.failure("X-API-Token 无效（请在管理后台设置 astrbot.api_token）"));
    }

    @GetMapping("/status")
    public ResponseEntity<Object> status(HttpServletRequest request) {
        if (!authorized(request)) {
            return denied();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("online", statsService.totalOnline());
        body.put("max", statsService.maxPlayers());
        body.put("servers", statsService.totalServers());
        body.put("version", statsService.version());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/players")
    public ResponseEntity<Object> players(HttpServletRequest request) {
        if (!authorized(request)) {
            return denied();
        }
        return ResponseEntity.ok(Map.of("servers", statsService.heartbeats().values()));
    }

    @PostMapping("/bind")
    public ResponseEntity<Object> bind(@RequestBody BindBody body, HttpServletRequest request) {
        if (!authorized(request)) {
            return denied();
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(body.minecraftName());
        if (userOpt.isEmpty() && userRepository.listAll().stream()
                .noneMatch(u -> body.minecraftName() != null
                        && body.minecraftName().equalsIgnoreCase(u.minecraftName()))) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("MC ID 不存在（需先注册白名单）"));
        }
        for (UserRecord u : userRepository.findAll()) {
            if (u.qqNumber() != null && u.qqNumber().equals(body.qq())) {
                userRepository.save(clearQq(u));
            }
        }
        var target = userRepository.findByUsernameIgnoreCase(body.minecraftName());
        if (target.isEmpty()) {
            // 按游戏名命中的账户
            target = userRepository.listAll().stream()
                    .filter(u -> body.minecraftName().equalsIgnoreCase(u.minecraftName())).findFirst();
        }
        if (target.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("账户不存在"));
        }
        userRepository.save(withQq(target.get(), body.qq()));
        return ResponseEntity.ok(ApiResponse.success("绑定成功"));
    }

    @PostMapping("/unbind")
    public ResponseEntity<Object> unbind(@RequestBody BindBody body, HttpServletRequest request) {
        if (!authorized(request)) {
            return denied();
        }
        for (UserRecord u : userRepository.findAll()) {
            if (body.qq() != null && body.qq().equals(u.qqNumber())
                    || body.minecraftName() != null && body.minecraftName().equalsIgnoreCase(u.username())) {
                userRepository.save(clearQq(u));
            }
        }
        return ResponseEntity.ok(ApiResponse.success("已解绑"));
    }

    @GetMapping("/lookup/qq/{qq}")
    public ResponseEntity<Object> lookupQq(@RequestParam String qq, HttpServletRequest request) {
        if (!authorized(request)) {
            return denied();
        }
        return ResponseEntity.ok(userRepository.listAll().stream()
                .filter(u -> qq.equals(u.qqNumber()))
                .findFirst()
                .map(u -> Map.of("found", true, "username", u.username(), "status", u.status()))
                .orElseGet(() -> Map.of("found", false)));
    }

    @GetMapping("/lookup/mc/{mc}")
    public ResponseEntity<Object> lookupMc(@RequestParam String mc, HttpServletRequest request) {
        if (!authorized(request)) {
            return denied();
        }
        return ResponseEntity.ok(userRepository.listAll().stream()
                .filter(u -> mc.equalsIgnoreCase(u.username()) || mc.equalsIgnoreCase(u.minecraftName()))
                .findFirst()
                .map(u -> Map.of("found", true, "qq", u.qqNumber() == null ? "" : u.qqNumber()))
                .orElseGet(() -> Map.of("found", false)));
    }

    @PostMapping("/chat")
    public ResponseEntity<Object> chat(@RequestBody ChatBody body, HttpServletRequest request) {
        if (!authorized(request)) {
            return denied();
        }
        String sender = body.sender() == null || body.sender().isBlank() ? "QQ用户" : body.sender();
        chatService.broadcast("[QQ] " + sender, body.message() == null ? "" : body.message());
        return ResponseEntity.ok(ApiResponse.success("已广播"));
    }

    private UserRecord withQq(UserRecord user, String qq) {
        return new UserRecord(user.id(), user.username(), user.email(), user.status(),
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                qq, System.currentTimeMillis(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), user.banReason(), user.banTime(), user.avatar());
    }

    private UserRecord clearQq(UserRecord user) {
        return new UserRecord(user.id(), user.username(), user.email(), user.status(),
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                null, null, user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), user.banReason(), user.banTime(), user.avatar());
    }
}
