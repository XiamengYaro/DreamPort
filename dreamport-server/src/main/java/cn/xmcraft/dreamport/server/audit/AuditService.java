package cn.xmcraft.dreamport.server.audit;

import org.springframework.stereotype.Service;

import java.util.List;

/** 审计日志服务（修复旧版操作不落审计的点，Rules.md §9） */
@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void log(String action, String operator, String target, String detail) {
        auditRepository.save(AuditRecord.of(action, operator, target, detail));
    }

    public List<AuditRecord> recent() {
        return auditRepository.findTop200ByOrderByCreatedAtDesc();
    }
}
