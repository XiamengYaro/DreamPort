package cn.xmcraft.dreamport.server.audit;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface AuditRepository extends CrudRepository<AuditRecord, Long> {

    List<AuditRecord> findTop200ByOrderByCreatedAtDesc();
}
