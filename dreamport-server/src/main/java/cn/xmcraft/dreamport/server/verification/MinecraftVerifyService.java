package cn.xmcraft.dreamport.server.verification;

import cn.xmcraft.dreamport.server.review.ReviewService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ID 验证域：MC/基岩 ID 绑定 + 进服比对 pending_logins（对齐旧版 UserMinecraftHandler/UserBedrockHandler）。
 * 另维护 whitelist 指令队列（bukkit 白名单模式，P5 插件轮询执行）。
 */
@Service
public class MinecraftVerifyService {

    private static final Logger log = LoggerFactory.getLogger(MinecraftVerifyService.class);

    private final PendingLoginRepository pendingLoginRepository;
    private final UserRepository userRepository;
    private final ReviewService reviewService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final cn.xmcraft.dreamport.server.settings.SystemSettingsService systemSettings;
    /** bukkit 模式下待执行的 whitelist 指令（serverId → 指令），插件经 /internal/v1/commands/whitelist 领取 */
    private final Map<String, ConcurrentLinkedQueue<String>> whitelistCommands = new ConcurrentHashMap<>();

    public MinecraftVerifyService(PendingLoginRepository pendingLoginRepository,
                                  UserRepository userRepository, ReviewService reviewService,
                                  org.springframework.jdbc.core.JdbcTemplate jdbc,
                                  cn.xmcraft.dreamport.server.settings.SystemSettingsService systemSettings) {
        this.pendingLoginRepository = pendingLoginRepository;
        this.userRepository = userRepository;
        this.reviewService = reviewService;
        this.jdbc = jdbc;
        this.systemSettings = systemSettings;
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public Result setMinecraftId(String username, String minecraftName) {
        var conflict = userRepository.findByUsernameIgnoreCase(minecraftName);
        if (conflict.isPresent() && !conflict.get().username().equalsIgnoreCase(username)) {
            // 该名字已注册为账户；再查是否已被其他账户绑定为 minecraft_name
        }
        var taken = userRepository.listAll().stream()
                .filter(u -> u.minecraftName() != null
                        && u.minecraftName().equalsIgnoreCase(minecraftName)
                        && !u.username().equalsIgnoreCase(username))
                .findFirst();
        if (taken.isPresent()) {
            return Result.fail("该 Minecraft ID 已被其他账户绑定");
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            return Result.fail("用户不存在");
        }
        UserRecord user = userOpt.get();
        // 不在此处生成 UUID：真 UUID 以玩家实际进服记录为准（verifyMinecraft 时采用）
        UserRecord updated = withMinecraft(user, minecraftName, user.minecraftUuid(), "pending_verify");
        userRepository.save(updated);
        return Result.ok("已绑定，请在 3 分钟内使用该 ID 进服完成验证");
    }

    /** 玩家在网页点击"验证"：比对最近进服记录 */
    public Result verifyMinecraft(String username) {
        var userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            return Result.fail("用户不存在");
        }
        UserRecord user = userOpt.get();
        if (user.minecraftName() == null) {
            return Result.fail("请先绑定 Minecraft ID");
        }
        // 幂等：已完成过身份验证且无新的进服记录 → 直接成功
        boolean hasNewRecord = pendingLoginRepository
                .findLatestVerifiable(user.minecraftName(), System.currentTimeMillis()).isPresent();
        if (user.minecraftUuid() != null && !hasNewRecord) {
            return Result.ok("已完成验证");
        }
        if (!hasNewRecord) {
            return Result.fail("未检测到该 ID 的进服记录，请使用 " + user.minecraftName() + " 进服后重试");
        }
        Optional<PendingLoginRecord> login = pendingLoginRepository
                .findLatestVerifiable(user.minecraftName(), System.currentTimeMillis());
        PendingLoginRecord record = login.get();
        // UUID 比对优先：已绑定 UUID 时进服记录必须一致（防同名冒充）
        if (user.minecraftUuid() != null && !user.minecraftUuid().equalsIgnoreCase(record.minecraftUuid())) {
            return Result.fail("进服记录与绑定账号不匹配（UUID 不一致），请确认使用同一账号进服");
        }
        pendingLoginRepository.save(new PendingLoginRecord(record.id(), record.minecraftName(),
                record.minecraftUuid(), record.ipAddress(), record.loginTime(), true, record.expireTime()));
        // 采用进服记录中的真实 UUID；状态流转：
        // pending_verify → approved；pending 且问卷未启用 → approved（验证即完成白名单）；
        // pending 且问卷启用 → 保持 pending（还需答题）
        boolean questionnaireOn = systemSettings.questionnaireConfig()
                .getOrDefault("enabled", true).equals(Boolean.TRUE);
        String newStatus = user.status();
        if ("pending_verify".equals(user.status())
                || ("pending".equals(user.status()) && !questionnaireOn)) {
            newStatus = "approved";
        }
        UserRecord updated = withMinecraft(user, user.minecraftName(),
                record.minecraftUuid(), newStatus);
        userRepository.save(updated);
        enqueueWhitelist("main", "whitelist add " + user.minecraftName());
        log.info("[验证] {} 完成MC ID验证（{} / {}）", username, user.minecraftName(), record.minecraftUuid());
        return Result.ok("验证成功");
    }

    public Result setBedrockId(String username, String bedrockName, String prefix) {
        var userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            return Result.fail("用户不存在");
        }
        UserRecord user = userOpt.get();
        String full = bedrockName.startsWith(prefix) ? bedrockName : prefix + bedrockName;
        UserRecord updated = new UserRecord(user.id(), user.username(), user.email(), user.status(),
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), null, full, false, null,
                user.banReason(), user.banTime(), user.avatar());
        userRepository.save(updated);
        return Result.ok("已绑定基岩 ID，请使用基岩版进服完成验证");
    }

    /** 取消基岩验证:整体清空基岩字段(修复取消后残留"."陷入待验证态) */
    public Result clearBedrock(String username) {
        var userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            return Result.fail("用户不存在");
        }
        UserRecord user = userOpt.get();
        UserRecord updated = new UserRecord(user.id(), user.username(), user.email(), user.status(),
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), null, null, false, null,
                user.banReason(), user.banTime(), user.avatar());
        userRepository.save(updated);
        return Result.ok("已取消基岩版验证");
    }

    public Result verifyBedrock(String username) {
        var userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty() || userOpt.get().bedrockName() == null) {
            return Result.fail("请先绑定基岩 ID");
        }
        Optional<PendingLoginRecord> login = pendingLoginRepository
                .findLatestVerifiable(userOpt.get().bedrockName(), System.currentTimeMillis());
        if (login.isEmpty()) {
            return Result.fail("未检测到该基岩 ID 的进服记录");
        }
        UserRecord user = userOpt.get();
        UserRecord updated = new UserRecord(user.id(), user.username(), user.email(),
                "approved".equals(user.status()) ? "approved" : "approved",
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), login.get().minecraftUuid(),
                user.bedrockName(), true, System.currentTimeMillis(),
                user.banReason(), user.banTime(), user.avatar());
        userRepository.save(updated);
        return Result.ok("基岩版验证成功");
    }

    /** 插件上报登录尝试 */
    public void recordLogin(String name, String uuid, String ip) {
        pendingLoginRepository.save(new PendingLoginRecord(null, name, uuid, ip,
                System.currentTimeMillis(), null, null));
        jdbc.update("DELETE FROM dp_pending_login WHERE expire_time < ?", System.currentTimeMillis());
    }

    public void enqueueWhitelist(String serverId, String command) {
        whitelistCommands.computeIfAbsent(serverId, k -> new ConcurrentLinkedQueue<>()).add(command);
    }

    /** 插件领取并清空指令队列 */
    public List<String> drainWhitelistCommands(String serverId) {
        var queue = whitelistCommands.get(serverId);
        if (queue == null) {
            return List.of();
        }
        var drained = new java.util.ArrayList<String>();
        String cmd;
        while ((cmd = queue.poll()) != null) {
            drained.add(cmd);
        }
        return drained;
    }

    private UserRecord withMinecraft(UserRecord user, String name, String uuid, String status) {
        return new UserRecord(user.id(), user.username(), user.email(), status,
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), uuid, name, user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(),
                user.bedrockName(), user.bedrockVerified(), user.bedrockVerifiedAt(),
                user.banReason(), user.banTime(), user.avatar());
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanup() {
        jdbc.update("DELETE FROM dp_pending_login WHERE expire_time < ?", System.currentTimeMillis());
    }
}
