package cn.xmcraft.dreamport.server.verification;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** 进服登录尝试记录（dp_pending_login），供网页 ID 验证比对（3 分钟窗口，对齐旧版） */
@Table("dp_pending_login")
public record PendingLoginRecord(
        @Id Long id,
        String minecraftName,
        String minecraftUuid,
        String ipAddress,
        Long loginTime,
        Boolean verified,
        Long expireTime
) {
    public PendingLoginRecord {
        if (verified == null) {
            verified = false;
        }
        if (expireTime == null) {
            expireTime = (loginTime == null ? System.currentTimeMillis() : loginTime) + 3 * 60_000L;
        }
    }

    public boolean verifiable(long now) {
        return !verified && expireTime > now;
    }
}
