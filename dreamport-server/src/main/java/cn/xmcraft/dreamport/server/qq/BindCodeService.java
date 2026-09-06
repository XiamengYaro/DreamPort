package cn.xmcraft.dreamport.server.qq;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * QQ 绑定验证码状态机（内存存储，重启失效可接受——docs/ASTRBOT_PLAN.md §5.2）。
 * 6 位数字码、5 分钟有效、单次消费；每 QQ 申请频控 1 次/分钟、5 次/天；
 * 同一 QQ 重新申请覆盖旧码；错误按提交者（"user:用户名" / "game:玩家名"）计数，
 * 10 分钟窗口内错 3 次锁定。
 */
@Service
public class BindCodeService {

    public static final long TTL_MS = 5 * 60 * 1000L;
    private static final long MINUTE_MS = 60 * 1000L;
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    private static final long LOCK_MS = 10 * 60 * 1000L;
    private static final int MAX_PER_DAY = 5;
    private static final int MAX_FAILS = 3;

    /** 申请结果；code 为 null 表示触发频控 */
    public record Issue(String code, long expiresIn) {
    }

    public enum Status { OK, BAD_CODE, LOCKED }

    public record Consume(Status status, String qq) {
        static Consume ok(String qq) { return new Consume(Status.OK, qq); }

        static Consume fail(Status s) { return new Consume(s, null); }
    }

    private record Entry(String qq, long expiresAt) {
    }

    private final java.util.function.LongSupplier clock;
    private final SecureRandom random = new SecureRandom();
    /** code → 条目 */
    private final Map<String, Entry> activeByCode = new ConcurrentHashMap<>();
    /** qq → 申请时间戳（频控） */
    private final Map<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();
    /** submitKey → {窗口起点, 失败次数} */
    private final Map<String, long[]> failLog = new ConcurrentHashMap<>();

    public BindCodeService() {
        this(System::currentTimeMillis);
    }

    /** 时钟可注入（单测覆盖频控/锁定窗口） */
    BindCodeService(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    private long now() {
        return clock.getAsLong();
    }

    /** 为 QQ 申请验证码；触发频控返回 null */
    public Issue request(String qq) {
        long now = now();
        Deque<Long> log = requestLog.computeIfAbsent(qq, k -> new ConcurrentLinkedDeque<>());
        synchronized (log) {
            log.removeIf(t -> now - t > DAY_MS);
            if (!log.isEmpty() && now - log.peekLast() < MINUTE_MS) {
                return null;
            }
            if (log.size() >= MAX_PER_DAY) {
                return null;
            }
            log.addLast(now);
        }
        // 覆盖同 QQ 旧码，顺带清过期
        activeByCode.values().removeIf(e -> e.qq().equals(qq) || e.expiresAt() <= now);
        String code = String.format("%06d", random.nextInt(1_000_000));
        activeByCode.put(code, new Entry(qq, now + TTL_MS));
        return new Issue(code, TTL_MS / 1000);
    }

    /** 校验并消费验证码（单次有效）；成功返回对应 QQ */
    public Consume consume(String code, String submitKey) {
        long now = now();
        if (submitKey != null && locked(submitKey, now)) {
            return Consume.fail(Status.LOCKED);
        }
        Entry entry = code == null ? null : activeByCode.get(code);
        if (entry == null) {
            if (code != null) {
                activeByCode.remove(code);
            }
            if (submitKey != null) {
                countFail(submitKey, now);
            }
            return Consume.fail(Status.BAD_CODE);
        }
        if (entry.expiresAt() <= now) {
            activeByCode.remove(code);
            return Consume.fail(Status.BAD_CODE);
        }
        activeByCode.remove(code);
        return Consume.ok(entry.qq());
    }

    /** 过期码与失败窗口清理 */
    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void sweep() {
        long now = now();
        activeByCode.values().removeIf(e -> e.expiresAt() <= now);
        failLog.values().removeIf(f -> now - f[0] >= LOCK_MS);
    }

    private boolean locked(String key, long now) {
        long[] f = failLog.get(key);
        return f != null && now - f[0] < LOCK_MS && f[1] >= MAX_FAILS;
    }

    private void countFail(String key, long now) {
        failLog.compute(key, (k, f) -> {
            if (f == null || now - f[0] >= LOCK_MS) {
                return new long[]{now, 1};
            }
            f[1]++;
            return f;
        });
    }
}
