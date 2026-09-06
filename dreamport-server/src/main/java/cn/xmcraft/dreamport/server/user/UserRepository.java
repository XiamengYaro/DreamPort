package cn.xmcraft.dreamport.server.user;

import org.springframework.data.repository.CrudRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends CrudRepository<UserRecord, Long> {

    /** 旧版默认用户名不区分大小写（register.username_case_sensitive=false） */
    Optional<UserRecord> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    /** ID 验证中：按绑定的 MC ID 查 pending_verify 账户 */
    Optional<UserRecord> findByMinecraftNameIgnoreCaseAndStatus(String minecraftName, String status);

    /** findAll() 返回 Iterable，统一转 List */
    default List<UserRecord> listAll() {
        List<UserRecord> list = new ArrayList<>();
        findAll().forEach(list::add);
        return list;
    };
}
