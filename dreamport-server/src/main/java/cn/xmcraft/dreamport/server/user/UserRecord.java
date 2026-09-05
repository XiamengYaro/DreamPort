package cn.xmcraft.dreamport.server.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 用户实体（dp_user），字段与旧版 UserData 全量对齐——数据迁移兼容的核心（Rules.md §4）。
 * qq_number/qq_bound_at 为新入库列（修复旧版 MySQL 模式下 QQ 绑定丢失问题）。
 */
@Table("dp_user")
public record UserRecord(
        @Id Long id,
        String username,
        String email,
        String status,
        String passwordAlgo,
        String passwordHash,
        Long regTime,
        String discordId,
        String qqNumber,
        Long qqBoundAt,
        Integer questionnaireScore,
        Boolean questionnairePassed,
        String questionnaireReviewSummary,
        Long questionnaireScoredAt,
        String questionnaireReasons,
        String questionnaireAnswers,
        String minecraftUuid,
        String minecraftName,
        Boolean microsoftVerified,
        Long verifiedAt,
        String verifyType,
        String invitedBy,
        String bedrockUuid,
        String bedrockName,
        Boolean bedrockVerified,
        Long bedrockVerifiedAt,
        String banReason,
        Long banTime,
        String avatar
) {

    public UserRecord {
        if (regTime == null) {
            regTime = System.currentTimeMillis();
        }
        if (questionnaireScore == null) {
            questionnaireScore = 0;
        }
        if (questionnairePassed == null) {
            questionnairePassed = false;
        }
        if (microsoftVerified == null) {
            microsoftVerified = false;
        }
        if (bedrockVerified == null) {
            bedrockVerified = false;
        }
    }

    /** 登录验证成功后的透明升级（legacy_sha256 → bcrypt） */
    public UserRecord withPassword(String newAlgo, String newHash) {
        return new UserRecord(id, username, email, status, newAlgo, newHash, regTime,
                discordId, qqNumber, qqBoundAt, questionnaireScore, questionnairePassed,
                questionnaireReviewSummary, questionnaireScoredAt, questionnaireReasons,
                questionnaireAnswers, minecraftUuid, minecraftName, microsoftVerified,
                verifiedAt, verifyType, invitedBy, bedrockUuid, bedrockName,
                bedrockVerified, bedrockVerifiedAt, banReason, banTime, avatar);
    }

    public boolean approved() {
        return "approved".equals(status);
    }

    public boolean banned() {
        return "banned".equals(status);
    }
}
