package cn.xmcraft.dreamport.server.avatar;

import cn.xmcraft.dreamport.server.settings.SystemSettingsService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大头照渲染服务(双层皮肤:基础脸 8x8 + 帽子层 8x8 叠加,最近邻放大)。
 *
 * 皮肤来源(严格分流,替换 crafthead 外网依赖):
 * 1. 正版身份(microsoftVerified=true)→ Mojang session server 官方皮肤,绝不经手皮肤站
 * 2. 非正版 → BlessingSkin 插件接口(dreamport/api/skin,共享密钥)→ BS 材质文件
 * 3. 兜底 → 程序绘制默认脸(Steve/Alex 按名字 hash)
 *
 * 缓存两级:浏览器 Cache-Control+ETag(1h);服务端内存 LRU(皮肤图,TTL 1h)。
 */
@Service
public class AvatarRenderService {

    private static final Logger log = LoggerFactory.getLogger(AvatarRenderService.class);
    private static final long SKIN_TTL_MS = 3_600_000L;
    private static final int CACHE_CAP = 2000;
    private static final String MOJANG_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final UserRepository userRepository;
    private final SystemSettingsService settingsService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public AvatarRenderService(UserRepository userRepository, SystemSettingsService settingsService) {
        this.userRepository = userRepository;
        this.settingsService = settingsService;
    }

    /** 渲染结果:PNG 字节 + 内容 ETag(皮肤 hash) */
    public record AvatarImage(byte[] png, String etag) {}

    /** 包内可见(单测合成断言使用) */
    record SkinData(BufferedImage image, String etag) {}

    private record CacheEntry(SkinData skin, long at) {}

    /** 皮肤 LRU(访问序,上限 2000,TTL 1h) */
    private final Map<String, CacheEntry> skinCache =
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > CACHE_CAP;
                }
            };

    /** 渲染大头照;size 已由控制器钳制 */
    public AvatarImage render(String name, int size) throws Exception {
        SkinData skin = skinFor(name);
        // 默认脸(width=8)已是合成后的成品;64 宽皮肤才需要双层合成
        BufferedImage face = skin.image().getWidth() == 8 ? skin.image() : compositeFace(skin.image());
        BufferedImage out = scale(face, size);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bos);
        return new AvatarImage(bos.toByteArray(), skin.etag());
    }

    // ---------- 皮肤解析 ----------

    private synchronized SkinData skinFor(String name) {
        CacheEntry hit = skinCache.get(name);
        if (hit != null && System.currentTimeMillis() - hit.at() < SKIN_TTL_MS) {
            return hit.skin();
        }
        SkinData skin = resolveSkin(name);
        skinCache.put(name, new CacheEntry(skin, System.currentTimeMillis()));
        return skin;
    }

    private SkinData resolveSkin(String name) {
        // 1. 正版身份 → Mojang 官方皮肤(绝不经手皮肤站)
        var userOpt = userRepository.findByMinecraftNameIgnoreCase(name);
        if (userOpt.isPresent()) {
            var u = userOpt.get();
            if (Boolean.TRUE.equals(u.microsoftVerified()) && u.minecraftUuid() != null) {
                try {
                    SkinData s = mojangSkin(u.minecraftUuid());
                    if (s != null) return s;
                } catch (Exception e) {
                    log.warn("[头像] Mojang 皮肤获取失败 {}: {}", name, e.getMessage());
                }
            }
        }
        // 2. 非正版 → 皮肤站插件接口
        if (bsReady()) {
            try {
                SkinData s = blessingSkinSkin(name);
                if (s != null) return s;
            } catch (Exception e) {
                log.warn("[头像] 皮肤站皮肤获取失败 {}: {}", name, e.getMessage());
            }
        }
        // 3. 兜底:程序绘制默认脸
        return defaultFace(name);
    }

    /** Mojang 官方皮肤(正版身份专用) */
    private SkinData mojangSkin(String uuid) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(MOJANG_PROFILE + uuid + "?unsigned=false"))
                .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;
        JsonNode textures = null;
        for (JsonNode p : mapper.readTree(resp.body()).path("properties")) {
            if ("textures".equals(p.path("name").asText())) {
                textures = mapper.readTree(Base64.getDecoder().decode(p.path("value").asText()));
                break;
            }
        }
        if (textures == null) return null;
        String url = textures.path("textures").path("SKIN").path("url").asText("");
        if (url.isBlank()) return null;
        return downloadSkin(url);
    }

    /** 皮肤站皮肤(插件按名字解析角色→材质) */
    private SkinData blessingSkinSkin(String name) throws Exception {
        var cfg = settingsService.blessingskinConfig();
        if (!Boolean.TRUE.equals(cfg.get("enabled"))) return null;
        String url = str(cfg.get("url"));
        String secret = str(cfg.get("apiSecret"));
        if (url.isBlank() || secret.isBlank()) return null;
        HttpRequest req = HttpRequest.newBuilder(URI.create(strip(url) + "/dreamport/api/skin?name=" + enc(name)))
                .header("X-Dreamport-Secret", secret)
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200 || resp.body().length < 100) return null;
        return skinFromBytes(resp.body());
    }

    private SkinData downloadSkin(String url) throws Exception {
        HttpResponse<byte[]> resp = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) return null;
        return skinFromBytes(resp.body());
    }

    private SkinData skinFromBytes(byte[] png) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        if (img == null || img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) return null;
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(png)) hex.append(String.format("%02x", b));
        return new SkinData(img, hex.toString());
    }

    // ---------- 渲染(静态,便于单测) ----------

    /** 双层合成:基础脸 (8,8,8,8) + 帽子层 (40,8,8,8) alpha 叠加 → 8x8 */
    static BufferedImage compositeFace(BufferedImage skin) {
        BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = face.createGraphics();
        g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
        g.dispose();
        BufferedImage hat = skin.getSubimage(40, 8, 8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int argb = hat.getRGB(x, y);
                if ((argb >>> 24) != 0) {
                    face.setRGB(x, y, argb);
                }
            }
        }
        return face;
    }

    /** 最近邻放大 */
    static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    /** 默认脸(Steve/Alex 按名字 hash),无帽子层 */
    static SkinData defaultFace(String name) {
        boolean alex = Math.floorMod(name.toLowerCase().hashCode(), 2) == 1;
        int hair = alex ? 0xFFC98555 : 0xFF2B1B0E;
        int skinTone = alex ? 0xFFF8C59B : 0xFFBD8B72;
        int skinDark = alex ? 0xFFE0A87E : 0xFF9C6F58;
        int eyeWhite = 0xFFFFFFFF;
        int pupil = alex ? 0xFF3F7E44 : 0xFF4A3D8C;
        BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int c;
                if (y <= 1) c = hair;
                else if (y == 2) c = (x == 0 || x == 7) ? hair : skinTone;
                else if (y == 4 && (x == 1 || x == 6)) c = eyeWhite;
                else if (y == 4 && (x == 2 || x == 5)) c = pupil;
                else if (y == 6 && (x == 3 || x == 4)) c = skinDark;
                else c = skinTone;
                face.setRGB(x, y, c);
            }
        }
        String etag = alex ? "default-alex" : "default-steve";
        return new SkinData(face, etag);
    }

    // ---------- 工具 ----------

    private boolean bsReady() {
        var cfg = settingsService.blessingskinConfig();
        return Boolean.TRUE.equals(cfg.get("enabled"))
                && str(cfg.get("url")).isBlank() == false
                && str(cfg.get("apiSecret")).isBlank() == false;
    }

    private String strip(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
