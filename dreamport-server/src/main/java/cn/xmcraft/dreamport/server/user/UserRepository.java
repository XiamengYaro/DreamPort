package cn.xmcraft.dreamport.server.user;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<UserRecord, Long> {

    /** 旧版默认用户名不区分大小写（register.username_case_sensitive=false） */
    Optional<UserRecord> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);
}
