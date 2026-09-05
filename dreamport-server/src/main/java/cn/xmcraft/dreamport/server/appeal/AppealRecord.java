package cn.xmcraft.dreamport.server.appeal;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** 问卷申诉（dp_appeal），status: pending → approved/rejected */
@Table("dp_appeal")
public record AppealRecord(
        @Id Long id,
        String username,
        String reason,
        String status,
        String adminReply,
        Long createdAt,
        Long resolvedAt,
        String resolvedBy
) {
    public AppealRecord {
        if (status == null) {
            status = "pending";
        }
        if (createdAt == null) {
            createdAt = System.currentTimeMillis();
        }
    }
}
