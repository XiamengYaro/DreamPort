package cn.xmcraft.dreamport.server.avatar;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarRenderServiceTest {

    /** 64x64 皮肤:基础脸(8,8)-(15,15) 红色;帽子层默认透明,仅 (40,9)(41,9) 蓝色 */
    private BufferedImage skin64() {
        BufferedImage s = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) s.setRGB(x, y, 0xFFFF0000);
        }
        s.setRGB(40, 9, 0xFF0000FF);
        s.setRGB(41, 9, 0xFF0000FF);
        return s;
    }

    @Test
    void 双层合成_帽子层按alpha覆盖基础脸() {
        BufferedImage face = AvatarRenderService.compositeFace(skin64());
        // 帽子不透明像素覆盖基础脸:皮肤 (40,9)(41,9) → 脸 (0,1)(1,1)
        assertEquals(0xFF0000FF, face.getRGB(0, 1) & 0xFFFFFFFF);
        assertEquals(0xFF0000FF, face.getRGB(1, 1) & 0xFFFFFFFF);
        // 帽子透明处保留基础脸颜色
        assertEquals(0xFFFF0000, face.getRGB(0, 0) & 0xFFFFFFFF);
        assertEquals(0xFFFF0000, face.getRGB(7, 7) & 0xFFFFFFFF);
    }

    @Test
    void 兼容旧版64x32皮肤() {
        BufferedImage legacy = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) legacy.setRGB(x, y, 0xFF00FF00);
        }
        BufferedImage face = AvatarRenderService.compositeFace(legacy);
        assertEquals(0xFF00FF00, face.getRGB(3, 3) & 0xFFFFFFFF);
    }

    @Test
    void 最近邻放大到目标尺寸() {
        BufferedImage face = AvatarRenderService.compositeFace(skin64());
        BufferedImage out = AvatarRenderService.scale(face, 64);
        assertEquals(64, out.getWidth());
        assertEquals(64, out.getHeight());
        // 脸 (0,0) 红色 → 放大后 (4,4) 仍红;脸 (0,1) 蓝 → (4,8) 蓝(整块 8x8 同色)
        assertEquals(0xFFFF0000, out.getRGB(4, 4) & 0xFFFFFFFF);
        assertEquals(0xFF0000FF, out.getRGB(4, 8) & 0xFFFFFFFF);
        assertEquals(0xFF0000FF, out.getRGB(7, 15) & 0xFFFFFFFF);
    }

    @Test
    void 默认脸不透明且同名稳定_异名可能不同() {
        var steve = AvatarRenderService.defaultFace("Steve");
        var steve2 = AvatarRenderService.defaultFace("Steve");
        assertEquals(steve.etag(), steve2.etag());
        assertNotSame(steve.image(), steve2.image());
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assertNotEquals(0, steve.image().getRGB(x, y) & 0xFFFFFFFF, "默认脸不允许透明像素");
            }
        }
        // 两个不同名字在两种模型间至少覆盖一种(etag 只会是 default-steve / default-alex)
        String e1 = AvatarRenderService.defaultFace("a").etag();
        assertTrue(e1.equals("default-steve") || e1.equals("default-alex"));
    }

    /** 覆盖查档 HTTP 层:Notch 返回固定官方 UUID,其它名字视为官方库不存在 */
    private AvatarRenderService stubService(java.util.concurrent.atomic.AtomicInteger calls) {
        return new AvatarRenderService() {
            @Override
            String fetchOfficialUuid(String name) {
                calls.incrementAndGet();
                return name.equals("Notch") ? "069a79f444e94726a5befca90e38aaf5" : null;
            }
        };
    }

    @Test
    void 按名查档_命中与未命中都走缓存() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var svc = stubService(calls);
        assertEquals("069a79f444e94726a5befca90e38aaf5", svc.officialUuidByName("Notch"));
        assertEquals("069a79f444e94726a5befca90e38aaf5", svc.officialUuidByName("Notch"));
        assertEquals(1, calls.get(), "命中缓存不应重复发起查档");
        assertEquals(null, svc.officialUuidByName("Nobody"));
        assertEquals(2, calls.get());
        assertEquals(null, svc.officialUuidByName("Nobody"));
        assertEquals(2, calls.get(), "未命中缓存同样生效");
    }

    @Test
    void 按名查档_过期后重新查档() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var svc = stubService(calls);
        assertEquals("069a79f444e94726a5befca90e38aaf5", svc.officialUuidByName("Notch"));
        // 手工把缓存条目拨回 25h 前(超过 24h 命中 TTL)
        var e = svc.uuidCache.get("Notch");
        svc.uuidCache.put("Notch", new AvatarRenderService.UuidCacheEntry(e.uuid(), e.at() - 25 * 3_600_000L));
        svc.officialUuidByName("Notch");
        assertEquals(2, calls.get(), "过期缓存应重新查档");
    }

    @Test
    void 查档未命中_降级默认脸() {
        var svc = stubService(new java.util.concurrent.atomic.AtomicInteger());
        var skin = svc.resolveSkin("Nobody");
        assertTrue(skin.etag().equals("default-steve") || skin.etag().equals("default-alex"));
    }
}
