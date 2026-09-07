package cn.xmcraft.dreamport.server.avatar;

import cn.xmcraft.dreamport.server.web.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大头照渲染端点(公开,浏览器可缓存):
 * GET /api/avatar/{name}?size=N —— name=游戏 ID;双层皮肤(基础脸+帽子层);
 * 缓存:Cache-Control 1h + ETag(皮肤内容 hash,换肤最迟 1h 生效)。
 */
@RestController
public class AvatarController {

    private final AvatarRenderService renderService;
    private final RateLimiter rateLimiter;

    public AvatarController(AvatarRenderService renderService, RateLimiter rateLimiter) {
        this.renderService = renderService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/api/avatar/{name}")
    public ResponseEntity<byte[]> avatar(@PathVariable String name,
                                         @RequestParam(defaultValue = "64") int size,
                                         HttpServletRequest request) throws Exception {
        if (name == null || name.isBlank() || name.length() > 32 || !name.matches("[A-Za-z0-9_.\\-\\u4e00-\\u9fff]+")) {
            return ResponseEntity.badRequest().build();
        }
        int px = Math.max(8, Math.min(256, size));
        if (!rateLimiter.allow("avatar:" + clientIp(request), 600, 60_000)) {
            return ResponseEntity.status(429).build();
        }
        AvatarRenderService.AvatarImage img = renderService.render(name, px);
        String etag = "\"" + img.etag() + "\"";
        String inm = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (etag.equals(inm)) {
            return ResponseEntity.status(304)
                    .header(HttpHeaders.ETAG, etag)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                    .build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .header(HttpHeaders.ETAG, etag)
                .contentType(MediaType.IMAGE_PNG)
                .body(img.png());
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
