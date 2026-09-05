package cn.xmcraft.dreamport.server.village;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface VillageTradeRepository extends CrudRepository<VillageTradeRecord, Long> {

    List<VillageTradeRecord> findByStatusOrderByCreatedAtDesc(String status);

    List<VillageTradeRecord> findAllByOrderByCreatedAtDesc();
}
