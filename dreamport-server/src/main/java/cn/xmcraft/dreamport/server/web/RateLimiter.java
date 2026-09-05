package cn.xmcraft.dreamport.server.web;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 维度滑动窗口限流（阈值沿用旧版：登录 5/min、注册 3/min、验证码 3/5min，Rules.md §3）。
 * P1 简化实现：进程内计数；P8 视压测结果决定是否换 bucket4j/持久化。
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** @return true=放行；false=超限 */
    public boolean allow(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        boolean[] allowed = new boolean[1];
        hits.compute(key, (k, deque) -> {
            Deque<Long> q = deque == null ? new ArrayDeque<>() : deque;
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
                q.pollFirst();
            }
            if (q.size() < limit) {
                q.addLast(now);
                allowed[0] = true;
            } else {
                allowed[0] = false;
            }
            return q;
        });
        return allowed[0];
    }

    /** 周期性清理空队列，避免长期运行内存膨胀 */
    public void cleanup(long windowMs) {
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(e -> {
            Deque<Long> q = e.getValue();
            synchronized (q) {
                while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
                    q.pollFirst();
                }
                return q.isEmpty();
            }
        });
    }
}
