package cn.xmcraft.dreamport.server.verification;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface PendingLoginRepository extends CrudRepository<PendingLoginRecord, Long> {

    /** 最近一条可验证（未验证且未过期）记录 */
    @Query("""
            SELECT * FROM dp_pending_login
            WHERE LOWER(minecraft_name) = LOWER(:name) AND verified = FALSE AND expire_time > :now
            ORDER BY login_time DESC LIMIT 1
            """)
    Optional<PendingLoginRecord> findLatestVerifiable(String name, long now);

    List<PendingLoginRecord> findTop100ByOrderByLoginTimeDesc();
}
