package cn.xmcraft.dreamport.server.qq;

import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import org.springframework.stereotype.Service;

/**
 * QQ↔账号绑定操作：单绑定语义（一 QQ 一账号，新绑定清除旧绑定，与 M1 前 bind 行为一致）。
 * dp_user.qq_number / qq_bound_at 是唯一数据权威（docs/ASTRBOT_PLAN.md §5.2）。
 */
@Service
public class QqBindingService {

    private final UserRepository userRepository;

    public QqBindingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 绑定 QQ 到目标账号；先清除该 QQ 在其他账号上的旧绑定 */
    public synchronized void bind(UserRecord target, String qq) {
        for (UserRecord u : userRepository.listAll()) {
            if (u.qqNumber() != null && u.qqNumber().equals(qq) && !u.id().equals(target.id())) {
                userRepository.save(clearQq(u));
            }
        }
        userRepository.save(withQq(target, qq));
    }

    /** 按 QQ 解绑全部账号；@return 是否发生变更 */
    public synchronized boolean unbindQq(String qq) {
        boolean changed = false;
        for (UserRecord u : userRepository.listAll()) {
            if (qq != null && qq.equals(u.qqNumber())) {
                userRepository.save(clearQq(u));
                changed = true;
            }
        }
        return changed;
    }

    /** 展示用脱敏：保留前 3 后 2 */
    public static String mask(String qq) {
        if (qq == null || qq.isBlank()) {
            return "";
        }
        if (qq.length() < 5) {
            return "***";
        }
        return qq.substring(0, 3) + "****" + qq.substring(qq.length() - 2);
    }

    public UserRecord withQq(UserRecord user, String qq) {
        return new UserRecord(user.id(), user.username(), user.email(), user.status(),
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                qq, System.currentTimeMillis(), user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), user.banReason(), user.banTime(), user.banUntil(), user.avatar());
    }

    public UserRecord clearQq(UserRecord user) {
        return new UserRecord(user.id(), user.username(), user.email(), user.status(),
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                null, null, user.questionnaireScore(), user.questionnairePassed(),
                user.questionnaireReviewSummary(), user.questionnaireScoredAt(), user.questionnaireReasons(),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(), user.bedrockName(),
                user.bedrockVerified(), user.bedrockVerifiedAt(), user.banReason(), user.banTime(), user.banUntil(), user.avatar());
    }
}
