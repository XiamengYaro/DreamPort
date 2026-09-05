package cn.xmcraft.dreamport.server.verification;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface PasswordResetRepository extends CrudRepository<PasswordResetRecord, Long> {

    Optional<PasswordResetRecord> findByToken(String token);
}
