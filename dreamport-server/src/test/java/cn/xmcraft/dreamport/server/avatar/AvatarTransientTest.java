package cn.xmcraft.dreamport.server.avatar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 瞬时失败不缓存回归(修复"头像有时候显示默认脸"的根因):
 * Mojang 查档/皮肤获取的瞬时故障只影响本次响应,不得写入皮肤缓存。
 */
class AvatarTransientTest {

    /** fetchOfficialUuid 一直瞬时故障 */
    private AvatarRenderService failingService(java.util.concurrent.atomic.AtomicInteger calls) {
        return new AvatarRenderService() {
            @Override
            String fetchOfficialUuid(String name) throws Exception {
                calls.incrementAndGet();
                throw new java.net.ConnectException("simulated mojang down");
            }
        };
    }

    @Test
    void 瞬时失败_本次渲染默认脸但不写皮肤缓存() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var svc = failingService(calls);
        var img = svc.render("Notch", 8);
        assertTrue(img.etag().startsWith("default-"), "瞬时失败本次降级默认脸");
        synchronized (svc.skinCache) {
            assertEquals(0, svc.skinCache.size(), "瞬时失败不得写入皮肤缓存(否则默认脸钉 1 小时)");
        }
    }

    @Test
    void 瞬时失败_重试节流窗口内不再打Mojang() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var svc = failingService(calls);
        svc.render("Notch", 8);
        svc.render("Notch", 8);
        assertEquals(1, calls.get(), "30s 节流窗口内第二次请求不应再发起查档");
    }

    @Test
    void 官方库无此名_终态默认脸正常缓存() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var svc = new AvatarRenderService() {
            @Override
            String fetchOfficialUuid(String name) {
                calls.incrementAndGet();
                return null; // 204/404:官方库无此名(终态)
            }
        };
        var img = svc.render("Nobody", 8);
        assertTrue(img.etag().startsWith("default-"));
        synchronized (svc.skinCache) {
            assertEquals(1, svc.skinCache.size(), "终态默认脸应正常缓存");
        }
        svc.render("Nobody", 8);
        assertEquals(1, calls.get(), "终态默认脸命中缓存不重复查档");
    }
}
