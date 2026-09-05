package cn.xmcraft.dreamport.server.review;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.infra.MailService;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.websocket.ReviewPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 审核域：approve/reject/ban/unban（审计+邮件+WS 推送，对齐旧版 ReviewApplicationService）。
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final MailService mailService;
    private final ReviewPushService pushService;
    private final SettingService settingService;
    private final cn.xmcraft.dreamport.server.security.PasswordService passwordService;

    public ReviewService(UserRepository userRepository, AuditService auditService,
                         MailService mailService, ReviewPushService pushService,
                         SettingService settingService,
                         cn.xmcraft.dreamport.server.security.PasswordService passwordService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.mailService = mailService;
        this.pushService = pushService;
        this.settingService = settingService;
        this.passwordService = passwordService;
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    private Optional<UserRecord> target(String username) {
        return userRepository.findByUsernameIgnoreCase(username);
    }

    private void notify(String type, String username) {
        pushService.pushEvent(type, Map.of("username", username));
    }

    public Result approve(String username, String operator, String lang) {
        var userOpt = target(username);
        if (userOpt.isEmpty()) {
            return Result.fail("用户不存在");
        }
        UserRecord user = userRepository.save(withStatus(userOpt.get(), "approved"));
        auditService.log("approve", operator, username, null);
        if (user.email() != null && !user.email().isBlank()) {
            mailService.sendReviewApproved(username, user.email(), lang);
        }
        notify("user_approved", username);
        log.info("[审核] {} 通过了 {} 的申请", operator, username);
        return Result.ok("已通过 " + username + " 的白名单申请");
    }

    public Result reject(String username, String operator, String reason, String lang) {
        var userOpt = target(username);
        if (userOpt.isEmpty()) {
            return Result.fail("用户不存在");
        }
        userRepository.save(withStatus(userOpt.get(), "rejected"));
        auditService.log("reject", operator, username, reason);
        String email = userOpt.get().email();
        if (email != null && !email.isBlank()) {
            mailService.sendReviewRejected(username, reason == null ? "未通过审核" : reason, email, lang);
        }
        notify("user_rejected", username);
        return Result.ok("已拒绝 " + username + " 的申请");
    }

    public Result ban(String username, String operator, String reason) {
        var userOpt = target(username);
        if (userOpt.isEmpty()) {
            return Result.fail("用户不存在");
        }
        UserRecord user = userOpt.get();
        UserRecord updated = new UserRecord(user.id(), user.username(), user.email(), "banned",
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(),
                reason == null ? "违规操作" : reason, System.currentTimeMillis(), user.avatar());
        userRepository.save(updated);
        auditService.log("ban", operator, username, reason);
        notify("user_banned", username);
        return Result.ok("已封禁玩家 " + username);
    }

    public Result unban(String username, String operator) {
        var userOpt = target(username);
        if (userOpt.isEmpty()) {
            return Result.fail("用户不存在");
        }
        UserRecord user = userOpt.get();
        UserRecord updated = new UserRecord(user.id(), user.username(), user.email(), "approved",
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), null, 0L, user.avatar());
        userRepository.save(updated);
        auditService.log("unban", operator, username, null);
        notify("user_unbanned", username);
        return Result.ok("已解封玩家 " + username);
    }

    /** 管理员改状态（合法值校验对齐旧版 update-status） */
    public Result forceStatus(String username, String operator, String status) {
        var legal = java.util.Set.of("pending", "pending_review", "pending_verify", "invited_pending",
                "approved", "rejected", "banned");
        if (!legal.contains(status)) {
            return Result.fail("非法状态值");
        }
        var userOpt = target(username);
        if (userOpt.isEmpty()) {
            return Result.fail("用户不存在");
        }
        userRepository.save(withStatus(userOpt.get(), status));
        auditService.log("status_change", operator, username, status);
        return Result.ok("状态已更新");
    }

    public Result delete(String username, String operator) {
        var userOpt = target(username);
        if (userOpt.isEmpty()) {
            return Result.fail("用户不存在");
        }
        userRepository.delete(userOpt.get());
        auditService.log("delete", operator, username, null);
        return Result.ok("已删除用户 " + username);
    }

    public Result add(String username, String email, String status, String operator) {
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            return Result.fail("用户名已存在");
        }
        // 管理员代添加的账户无已知密码：随机 bcrypt 哈希占位（用户可通过忘记密码流程取回）
        UserRecord user = new UserRecord(null, username, email,
                status == null || status.isBlank() ? "approved" : status,
                "bcrypt", passwordService.hash(java.util.UUID.randomUUID().toString()), null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null);
        userRepository.save(user);
        auditService.log("add_user", operator, username, "email=" + email);
        return Result.ok("已添加用户 " + username);
    }

    private UserRecord withStatus(UserRecord user, String status) {
        return new UserRecord(user.id(), user.username(), user.email(), status,
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), user.banReason(), user.banTime(), user.avatar());
    }
}
