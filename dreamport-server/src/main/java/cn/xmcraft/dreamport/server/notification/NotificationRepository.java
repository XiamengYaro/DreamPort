package cn.xmcraft.dreamport.server.notification;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface NotificationRepository extends CrudRepository<NotificationRecord, Long> {

    List<NotificationRecord> findTop50ByUsernameIgnoreCaseOrderByCreatedAtDesc(String username);

    List<NotificationRecord> findByUsernameIgnoreCaseAndIsReadFalse(String username);

    List<NotificationRecord> findByUsernameIgnoreCase(String username);
}
