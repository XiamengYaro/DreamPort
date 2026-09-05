package cn.xmcraft.dreamport.server.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** 审计日志（dp_audit_log），语义对齐旧版 AuditRecord */
@Table("dp_audit_log")
public record AuditRecord(
        @Id Long id,
        String action,
        String operator,
        String target,
        String detail,
        Long createdAt
) {
    public AuditRecord {
        if (createdAt == null) {
            createdAt = System.currentTimeMillis();
        }
    }

    public static AuditRecord of(String action, String operator, String target, String detail) {
        return new AuditRecord(null, action, operator, target, detail, null);
    }
}
