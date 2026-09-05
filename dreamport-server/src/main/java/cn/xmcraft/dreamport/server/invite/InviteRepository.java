package cn.xmcraft.dreamport.server.invite;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface InviteRepository extends CrudRepository<InviteRecord, Long> {

    Optional<InviteRecord> findByCode(String code);

    List<InviteRecord> findByInviterUsernameIgnoreCaseOrderByCreatedAtDesc(String inviter);

    List<InviteRecord> findByStatus(String status);

    long countByInviterUsernameIgnoreCaseAndStatus(String inviter, String status);
}
