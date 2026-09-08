package cn.xmcraft.dreamport.server.points;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 游戏内奖励邮件:兑换/后台发放 → 入队(dp_mail)→ 插件拉取 → 玩家 GUI 领取执行 → 回执。
 * commands 为 JSON 字符串:[{"type":"command","cmd":"give {player} diamond 1"}]。
 */
@Service("rewardMailService")
public class MailService {

    private final JdbcTemplate jdbcTemplate;

    public MailService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void enqueue(String username, String title, String commandsJson, String note) {
        jdbcTemplate.update(
                "INSERT INTO dp_mail (username, title, commands, note, status, created_at) VALUES (?,?,?,?,'pending',?)",
                username, title, commandsJson, note, System.currentTimeMillis());
    }

    public List<Map<String, Object>> pending(String username) {
        return jdbcTemplate.queryForList(
                "SELECT id, title, commands, note, created_at FROM dp_mail "
                        + "WHERE username = ? AND status = 'pending' ORDER BY created_at",
                username);
    }

    /** 领取回执(幂等:仅 pending → claimed) */
    public boolean markClaimed(long id) {
        return jdbcTemplate.update(
                "UPDATE dp_mail SET status = 'claimed', claimed_at = ? WHERE id = ? AND status = 'pending'",
                System.currentTimeMillis(), id) > 0;
    }
}
