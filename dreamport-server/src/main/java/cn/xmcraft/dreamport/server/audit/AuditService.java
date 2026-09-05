package cn.xmcraft.dreamport.server.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 审计日志服务（修复旧版操作不落审计的点，Rules.md §9） */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void log(String action, String operator, String target, String detail) {
        auditRepository.save(AuditRecord.of(action, operator, target, detail));
        // 终端实时输出内容修改/管理操作
        log.info("[修改] {} {} {} {}",
                operator, action, target == null ? "" : target,
                detail == null || detail.isBlank() ? "" : "｜ " + detail);
    }

    public List<AuditRecord> recent() {
        return auditRepository.findTop200ByOrderByCreatedAtDesc();
    }
}
