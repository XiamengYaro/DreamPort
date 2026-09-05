package cn.xmcraft.dreamport.server.appeal;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface AppealRepository extends CrudRepository<AppealRecord, Long> {

    List<AppealRecord> findByStatusOrderByCreatedAtDesc(String status);

    List<AppealRecord> findAllByOrderByCreatedAtDesc();

    boolean existsByUsernameIgnoreCaseAndStatus(String username, String status);
}
