package cn.xmcraft.dreamport.server.qq;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * BindCodeService 状态机单测（纯内存 + 注入时钟，无 Spring 上下文）。
 */
class BindCodeServiceTest {

    @Test
    void requestIssuesSixDigitCode() {
        var service = new BindCodeService();
        var issue = service.request("10001");
        assertNotNull(issue);
        assertEquals(6, issue.code().length());
        assertEquals(300, issue.expiresIn());
    }

    @Test
    void consumeValidCodeReturnsQqAndIsSingleUse() {
        var service = new BindCodeService();
        String code = service.request("10001").code();
        var first = service.consume(code, "user:alice");
        assertEquals(BindCodeService.Status.OK, first.status());
        assertEquals("10001", first.qq());
        assertEquals(BindCodeService.Status.BAD_CODE, service.consume(code, "user:alice").status());
    }

    @Test
    void reRequestOverwritesPreviousCodeForSameQq() {
        AtomicLong clock = new AtomicLong(System.currentTimeMillis());
        var service = new BindCodeService(clock::get);
        String old = service.request("10001").code();
        clock.addAndGet(61_000); // 越过 1/min 限制
        String fresh = service.request("10001").code();
        assertEquals(BindCodeService.Status.BAD_CODE, service.consume(old, "user:alice").status());
        assertEquals("10001", service.consume(fresh, "user:alice").qq());
    }

    @Test
    void minuteRateLimitBlocksSecondRequest() {
        var service = new BindCodeService();
        assertNotNull(service.request("10001"));
        assertNull(service.request("10001"));
        // 其他 QQ 不受影响
        assertNotNull(service.request("10002"));
    }

    @Test
    void dailyLimitBlocksSixthRequestPerQq() {
        AtomicLong clock = new AtomicLong(System.currentTimeMillis());
        var service = new BindCodeService(clock::get);
        assertNotNull(service.request("10001"));
        for (int i = 0; i < 4; i++) {
            clock.addAndGet(61_000); // 逐次越过 1/min
            assertNotNull(service.request("10001"), "第 " + (i + 2) + " 次申请不应被日限拦截");
        }
        clock.addAndGet(61_000);
        assertNull(service.request("10001"), "第 6 次申请应被 5 次/天限制拦截");
        // 其他 QQ 不受影响
        assertNotNull(service.request("10002"));
    }

    @Test
    void threeFailsLocksSubmitterWithinWindow() {
        AtomicLong clock = new AtomicLong(System.currentTimeMillis());
        var service = new BindCodeService(clock::get);
        String code = service.request("10001").code();
        String submitter = "user:alice";
        for (int i = 0; i < 3; i++) {
            assertEquals(BindCodeService.Status.BAD_CODE, service.consume("000000", submitter).status());
        }
        assertEquals(BindCodeService.Status.LOCKED, service.consume(code, submitter).status());
        // 其他提交者不受影响
        assertEquals("10001", service.consume(code, "user:bob").qq());
    }

    @Test
    void lockExpiresAfterWindow() {
        AtomicLong clock = new AtomicLong(System.currentTimeMillis());
        var service = new BindCodeService(clock::get);
        String submitter = "user:alice";
        for (int i = 0; i < 3; i++) {
            service.consume("000000", submitter);
        }
        clock.addAndGet(11 * 60_000); // 越过 10 分钟锁定窗口
        String code = service.request("10002").code();
        assertEquals("10002", service.consume(code, submitter).qq());
    }

    @Test
    void wrongCodeDoesNotConsumeValidEntry() {
        var service = new BindCodeService();
        String code = service.request("10001").code();
        service.consume("999999", "user:alice");
        assertEquals("10001", service.consume(code, "user:bob").qq());
    }

    @Test
    void consumeNullCodeFailsSafely() {
        var service = new BindCodeService();
        assertEquals(BindCodeService.Status.BAD_CODE, service.consume(null, "user:alice").status());
    }
}
