package cn.xmcraft.dreamport.server.web;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 一次性流票据（修复审计：ChatBox 原把 JWT 拼进 ?token= URL，会进网关/代理日志）。
 * 登录后换取 60 秒有效的单次票据，仅用于打开 EventSource；消费后即失效。
 */
@Service
public class StreamTicketService {

    private static final long TTL_MS = 60_000L;

    private record Entry(String username, long expiresAt) {
    }

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();

    public String issue(String username) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new Entry(username, System.currentTimeMillis() + TTL_MS));
        return ticket;
    }

    /** 消费票据；无效/过期返回 null */
    public String consume(String ticket) {
        Entry e = ticket == null ? null : tickets.remove(ticket);
        if (e == null || System.currentTimeMillis() > e.expiresAt()) {
            return null;
        }
        return e.username();
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }
}
