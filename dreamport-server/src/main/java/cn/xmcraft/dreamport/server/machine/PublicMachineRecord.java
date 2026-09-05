package cn.xmcraft.dreamport.server.machine;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** 公共机器（dp_public_machine），status: pending → approved/rejected */
@Table("dp_public_machine")
public record PublicMachineRecord(
        @Id Long id,
        String name,
        String type,
        String world,
        Integer x,
        Integer y,
        Integer z,
        String builder,
        String usageText,
        String screenshotUrl,
        String status,
        String submitter,
        Long createdAt,
        String reviewedBy,
        Long reviewedAt
) {
    public PublicMachineRecord {
        if (status == null) {
            status = "pending";
        }
        if (createdAt == null) {
            createdAt = System.currentTimeMillis();
        }
    }
}
