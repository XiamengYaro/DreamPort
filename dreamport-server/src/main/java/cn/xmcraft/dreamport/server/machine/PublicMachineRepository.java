package cn.xmcraft.dreamport.server.machine;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface PublicMachineRepository extends CrudRepository<PublicMachineRecord, Long> {

    List<PublicMachineRecord> findByStatusOrderByCreatedAtDesc(String status);

    List<PublicMachineRecord> findAllByOrderByCreatedAtDesc();
}
