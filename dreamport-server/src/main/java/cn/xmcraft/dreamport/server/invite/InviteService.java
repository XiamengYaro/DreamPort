package cn.xmcraft.dreamport.server.invite;

import cn.xmcraft.dreamport.server.settings.SystemSettingsService;
import cn.xmcraft.dreamport.server.notification.NotificationRecord;
import cn.xmcraft.dreamport.server.notification.NotificationRepository;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 邀请域（对齐旧版 InviteService 状态机）：
 * 生成 → 被邀请人注册/申请（active→used, 用户→invited_pending）→ 邀请人 confirm（→pending_review）/ reject（→rejected）。
 */
@Service
public class InviteService {

    private final InviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final SystemSettingsService systemSettings;

    public InviteService(InviteRepository inviteRepository, UserRepository userRepository,
                         NotificationRepository notificationRepository, SystemSettingsService systemSettings) {
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
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

    private boolean isInviteEnabled() {
        var cfg = systemSettings.inviteConfig();
        return Boolean.TRUE.equals(cfg.getOrDefault("enabled", true));
    }

    private void notifyUser(String username, String type, String title, String message, String related) {
        notificationRepository.save(new NotificationRecord(null, username, type, title, message,
                null, null, related));
    }

    public Result generate(String inviter) {
        if (!isInviteEnabled()) {
            return Result.fail("邀请功能未启用");
        }
        Optional<UserRecord> inviterOpt = userRepository.findByUsernameIgnoreCase(inviter);
        if (inviterOpt.isEmpty() || !inviterOpt.get().approved()) {
            return Result.fail("仅已通过审核的玩家可以生成邀请码");
        }
        long activeCount = inviteRepository
                .countByInviterUsernameIgnoreCaseAndStatus(inviter, "active");
        var cfg = systemSettings.inviteConfig();
        if (activeCount >= ((Number) cfg.getOrDefault("maxInvitesPerUser", 3)).intValue()) {
            return Result.fail("活跃邀请码已达上限（" + ((Number) systemSettings.inviteConfig().getOrDefault("maxInvitesPerUser", 3)).intValue() + "）");
        }
        long now = System.currentTimeMillis();
        var cfg2 = systemSettings.inviteConfig();
        long expiryDays = ((Number) cfg2.getOrDefault("codeExpiryDays", 7)).longValue();
        InviteRecord invite = new InviteRecord(null, InviteRecord.generateCode(), inviter,
                null, "active", now, now + expiryDays * 86_400_000L, null);
        inviteRepository.save(invite);
        return Result.ok("邀请码已生成：" + invite.code());
    }

    public List<InviteRecord> myCodes(String inviter) {
        return inviteRepository.findByInviterUsernameIgnoreCaseOrderByCreatedAtDesc(inviter);
    }

    public List<InviteRecord> pendingFor(String inviter) {
        return inviteRepository.findByInviterUsernameIgnoreCaseOrderByCreatedAtDesc(inviter).stream()
                .filter(i -> "active".equals(i.status()) && i.inviteeUsername() == null
                        || "active".equals(i.status()) && i.inviteeUsername() != null
                        && userRepository.findByUsernameIgnoreCase(i.inviteeUsername())
                        .map(u -> "invited_pending".equals(u.status())).orElse(false))
                .toList();
    }

    /** 网页白名单页用码申请（pending 用户绑定邀请码） */
    public Result apply(String username, String code) {
        Optional<UserRecord> userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty() || !"pending".equals(userOpt.get().status())) {
            return Result.fail("仅待审核（pending）状态的用户可以使用邀请码");
        }
        Optional<InviteRecord> inviteOpt = inviteRepository.findByCode(code == null ? "" : code.trim());
        if (inviteOpt.isEmpty() || !inviteOpt.get().active(System.currentTimeMillis())) {
            return Result.fail("邀请码无效或已过期");
        }
        InviteRecord invite = inviteOpt.get();
        if (username.equalsIgnoreCase(invite.inviterUsername())) {
            return Result.fail("不能使用自己的邀请码");
        }
        inviteRepository.save(invite.withInvitee(username, "used", System.currentTimeMillis()));
        userRepository.save(withStatus(userOpt.get(), "invited_pending", username));
        notifyUser(invite.inviterUsername(), "invite_received", "收到玩家申请",
                username + " 使用了你的邀请码，请确认是否认识该玩家", username);
        return Result.ok("申请已提交，等待邀请人确认");
    }

    /** 注册时携带邀请码（免验证码路径） */
    public Result registerWithInvite(String username, String code) {
        Optional<InviteRecord> inviteOpt = inviteRepository.findByCode(code == null ? "" : code.trim());
        if (inviteOpt.isEmpty() || !inviteOpt.get().active(System.currentTimeMillis())) {
            return Result.fail("邀请码无效或已过期");
        }
        InviteRecord invite = inviteOpt.get();
        inviteRepository.save(invite.withInvitee(username, "used", System.currentTimeMillis()));
        Optional<UserRecord> userOpt = userRepository.findByUsernameIgnoreCase(username);
        userOpt.ifPresent(u -> userRepository.save(withStatus(u, "invited_pending", invite.inviterUsername())));
        notifyUser(invite.inviterUsername(), "invite_received", "邀请注册",
                username + " 使用你的邀请码注册，请确认", username);
        return Result.ok("注册成功，等待邀请人确认");
    }

    public Result confirm(String inviter, String invitee) {
        Optional<UserRecord> inviteeOpt = userRepository.findByUsernameIgnoreCase(invitee);
        if (inviteeOpt.isEmpty() || !"invited_pending".equals(inviteeOpt.get().status())
                || !invitee.equalsIgnoreCase(inviteeOpt.get().invitedBy())) {
            return Result.fail("该用户不在你的待确认邀请中");
        }
        Optional<UserRecord> inviterOpt = userRepository.findByUsernameIgnoreCase(inviter);
        if (inviterOpt.isEmpty() || !inviterOpt.get().approved()) {
            return Result.fail("仅已通过审核的玩家可以确认邀请");
        }
        userRepository.save(withStatus(inviteeOpt.get(), "pending_review", invitee));
        notifyUser(invitee, "invite_confirmed", "邀请已确认",
                inviter + " 已确认你的申请，进入管理员审核", inviter);
        return Result.ok("已确认 " + invitee + " 的申请");
    }

    public Result reject(String inviter, String invitee) {
        Optional<UserRecord> inviteeOpt = userRepository.findByUsernameIgnoreCase(invitee);
        if (inviteeOpt.isEmpty() || !"invited_pending".equals(inviteeOpt.get().status())
                || !invitee.equalsIgnoreCase(inviteeOpt.get().invitedBy())) {
            return Result.fail("该用户不在你的待确认邀请中");
        }
        userRepository.save(withStatus(inviteeOpt.get(), "rejected", invitee));
        notifyUser(invitee, "invite_rejected", "邀请被拒绝",
                inviter + " 拒绝了你的申请", inviter);
        return Result.ok("已拒绝 " + invitee + " 的申请");
    }

    private UserRecord withStatus(UserRecord user, String status, String invitedBy) {
        return new UserRecord(user.id(), user.username(), user.email(), status,
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), invitedBy, user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), user.banReason(), user.banTime(), user.avatar());
    }
}
