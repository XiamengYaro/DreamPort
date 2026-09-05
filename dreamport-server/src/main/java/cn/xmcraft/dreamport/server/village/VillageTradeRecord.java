package cn.xmcraft.dreamport.server.village;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** 村民族谱交易（dp_village_trade），status: pending → approved/rejected */
@Table("dp_village_trade")
public record VillageTradeRecord(
        @Id Long id,
        String playerName,
        String world,
        Integer x,
        Integer y,
        Integer z,
        String itemInput,
        String itemOutput,
        Double price,
        String status,
        Long createdAt,
        String reviewedBy,
        Long reviewedAt
) {
    public VillageTradeRecord {
        if (status == null) {
            status = "pending";
        }
        if (createdAt == null) {
            createdAt = System.currentTimeMillis();
        }
    }
}
