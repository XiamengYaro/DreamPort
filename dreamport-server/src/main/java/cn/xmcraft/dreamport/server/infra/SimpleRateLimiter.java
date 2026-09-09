package cn.xmcraft.dreamport.server.infra;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内简易限频(滑动窗口 + 每日配额):论坛发帖/回帖、反馈提交等用户生成内容防刷。
 * 单实例语义,重启清零——社区内容场景足够(聊天限频同款量级)。
 */
@Service
public class SimpleRateLimiter {

    /** key → 窗口内时间戳列表 */
    private final Map<String, ArrayWindow> windows = new ConcurrentHashMap<>();
    /** key → 当日计数(按天分桶) */
    private final Map<String, DayCounter> daily = new ConcurrentHashMap<>();

    /** 滑动窗口限频:windowMs 内最多 max 次 */
    public boolean allowWindow(String key, int max, long windowMs) {
        long now = System.currentTimeMillis();
        ArrayWindow w = windows.computeIfAbsent(key, k -> new ArrayWindow());
        synchronized (w) {
            w.dropBefore(now - windowMs);
            if (w.size() >= max) {
                return false;
            }
            w.add(now);
            return true;
        }
    }

    /** 每日配额(自然日,本地时区):当日最多 max 次 */
    public boolean allowDaily(String key, int max) {
        String day = java.time.LocalDate.now().toString();
        DayCounter c = daily.computeIfAbsent(key + ":" + day, k -> new DayCounter());
        synchronized (c) {
            if (c.count >= max) {
                return false;
            }
            c.count++;
            return true;
        }
    }

    /** 惰性清理:过期窗口与昨日日桶(每小时,防长期运行内存膨胀) */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 120_000)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - 24L * 3600_000;
        windows.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                e.getValue().dropBefore(cutoff);
                return e.getValue().size() == 0;
            }
        });
        String today = ":" + java.time.LocalDate.now();
        daily.keySet().removeIf(k -> !k.endsWith(today));
    }

    private static final class ArrayWindow {
        private final java.util.ArrayDeque<Long> stamps = new java.util.ArrayDeque<>();

        void dropBefore(long cutoff) {
            while (!stamps.isEmpty() && stamps.peekFirst() < cutoff) {
                stamps.pollFirst();
            }
        }

        int size() {
            return stamps.size();
        }

        void add(long t) {
            stamps.addLast(t);
        }
    }

    private static final class DayCounter {
        private int count;
    }
}
