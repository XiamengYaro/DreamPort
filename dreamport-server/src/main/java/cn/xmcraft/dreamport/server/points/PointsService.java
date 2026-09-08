package cn.xmcraft.dreamport.server.points;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 积分账本:余额(dp_points_balance)+ 流水(dp_points_ledger)同事务双写。
 * earn/spend 均幂等安全(spend 以条件 UPDATE 防透支)。
 */
@Service
public class PointsService {

    private final JdbcTemplate jdbcTemplate;

    public PointsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int balance(String username) {
        var r = jdbcTemplate.queryForList("SELECT balance FROM dp_points_balance WHERE username = ?", Integer.class, username);
        return r.isEmpty() ? 0 : r.get(0);
    }

    /** 入账(正数);返回新余额 */
    @Transactional
    public int earn(String username, int delta, String source, String ref, String note) {
        jdbcTemplate.update(
                "INSERT INTO dp_points_balance (username, balance, updated_at) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance), updated_at = VALUES(updated_at)",
                username, delta, System.currentTimeMillis());
        int bal = balance(username);
        ledger(username, delta, bal, source, ref, note);
        return bal;
    }

    /** 消费(扣减);余额不足抛 IllegalStateException */
    @Transactional
    public int spend(String username, int delta, String source, String ref, String note) {
        int updated = jdbcTemplate.update(
                "UPDATE dp_points_balance SET balance = balance - ?, updated_at = ? WHERE username = ? AND balance >= ?",
                delta, System.currentTimeMillis(), username, delta);
        if (updated == 0) {
            throw new IllegalStateException("积分不足");
        }
        int bal = balance(username);
        ledger(username, -delta, bal, source, ref, note);
        return bal;
    }

    private void ledger(String username, int delta, int bal, String source, String ref, String note) {
        jdbcTemplate.update(
                "INSERT INTO dp_points_ledger (username, delta, balance_after, source, ref, note, created_at) VALUES (?,?,?,?,?,?,?)",
                username, delta, bal, source, ref, note, System.currentTimeMillis());
    }
}
