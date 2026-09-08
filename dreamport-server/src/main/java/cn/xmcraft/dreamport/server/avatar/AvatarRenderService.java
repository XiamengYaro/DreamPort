package cn.xmcraft.dreamport.server.avatar;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 大头照渲染服务(双层皮肤:基础脸 8x8 + 帽子层 8x8 叠加,最近邻放大)。
 *
 * 皮肤来源(两层):
 * 1. 名字正版匹配 → 名字在 Mojang 官方库存在即视为正版身份,按官方 UUID 取 session server 官方皮肤
 *    (官方 UUID 缓存:命中 24h / 未命中 1h;Mojang 不可达自动降级且不缓存)
 * 2. 兜底 → 程序绘制默认脸(Steve/Alex 按名字 hash)
 *
 * 缓存三级:
 * - 浏览器 Cache-Control+ETag(1h,304 复验)
 * - 服务端内存 LRU(皮肤图 TTL 1h、官方 UUID 见上)
 * - 服务端磁盘持久化 data/avatar-cache/(官方皮肤 PNG,重启不丢):
 *   命中磁盘立即返回;文件超过 1h 由后台单线程静默刷新(显示永不等网络)
 * 冷缓存解析在锁外并发执行,批量头像不再逐个串行等待。
 */
@Service
public class AvatarRenderService {

    private static final Logger log = LoggerFactory.getLogger(AvatarRenderService.class);
    private static final long SKIN_TTL_MS = 3_600_000L;
    private static final long UUID_HIT_TTL_MS = 86_400_000L;
    private static final long UUID_MISS_TTL_MS = 3_600_000L;
    private static final int CACHE_CAP = 2000;
    private static final int UUID_CACHE_CAP = 4096;
    private static final String MOJANG_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String MOJANG_API = "https://api.mojang.com/users/profiles/minecraft/";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 渲染结果:PNG 字节 + 内容 ETag(皮肤 hash) */
    public record AvatarImage(byte[] png, String etag) {}

    /** 包内可见(单测合成断言使用) */
    record SkinData(BufferedImage image, String etag) {}

    private record CacheEntry(SkinData skin, long at) {}

    /** 包内可见(单测缓存断言使用);uuid=null 表示"官方库无此名"的未命中缓存 */
    record UuidCacheEntry(String uuid, long at) {}

    /** 皮肤内存 LRU(访问序,上限 2000,TTL 1h)——仅 在 synchronized(skinCache) 内读写 */
    private final Map<String, CacheEntry> skinCache =
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > CACHE_CAP;
                }
            };

    /** 官方 UUID 缓存(命中 24h / 未命中 1h);并发安全,超量时惰性清理过期项。包内可见(单测断言用) */
    final Map<String, UuidCacheEntry> uuidCache = new ConcurrentHashMap<>();

    /** 磁盘缓存目录(包内可见,单测可指向临时目录);官方皮肤 PNG 落盘,重启不丢 */
    Path cacheDir = Path.of("data", "avatar-cache");

    /** 后台刷新线程(单线程串行,避免过期风暴打爆 Mojang);显示层永远即时返回 */
    private final ExecutorService revalidatePool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "avatar-revalidate");
        t.setDaemon(true);
        return t;
    });
    private final Set<String> revalidating = ConcurrentHashMap.newKeySet();

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

    private SkinData skinFor(String name) throws Exception {
        long now = System.currentTimeMillis();
        synchronized (skinCache) {
            CacheEntry hit = skinCache.get(name);
            if (hit != null && now - hit.at() < SKIN_TTL_MS) {
                return hit.skin();
            }
        }

        // 磁盘命中:立即返回(永不等网络);文件超过 1h 触发后台静默刷新
        SkinData disk = loadFromDisk(name);
        if (disk != null) {
            synchronized (skinCache) {
                skinCache.put(name, new CacheEntry(disk, now));
            }
            if (Files.getLastModifiedTime(diskFile(name)).toMillis() < now - SKIN_TTL_MS) {
                revalidateAsync(name);
            }
            return disk;
        }

        // 首次遇到该名字:锁外并发解析(不同名字互不阻塞),成功后写内存+落盘
        SkinData skin = resolveSkin(name);
        synchronized (skinCache) {
            skinCache.put(name, new CacheEntry(skin, now));
        }
        persistToDisk(name, skin);
        return skin;
    }

    /** 过期皮肤后台静默刷新(单线程串行);期间显示层继续用磁盘/内存旧图 */
    private void revalidateAsync(String name) {
        if (!revalidating.add(name)) return;
        revalidatePool.execute(() -> {
            try {
                SkinData fresh = resolveSkin(name);
                synchronized (skinCache) {
                    skinCache.put(name, new CacheEntry(fresh, System.currentTimeMillis()));
                }
                persistToDisk(name, fresh);
            } catch (Exception e) {
                log.debug("[头像] 后台刷新失败 {}: {}", name, e.getMessage());
            } finally {
                revalidating.remove(name);
            }
        });
    }

    /** 包内可见(单测覆盖降级顺序用) */
    SkinData resolveSkin(String name) {
        // 1. 名字正版匹配 → Mojang 官方皮肤(名字在官方库存在即视为正版身份)
        String uuid = officialUuidByName(name);
        if (uuid != null) {
            try {
                SkinData s = mojangSkin(uuid);
                if (s != null) return s;
            } catch (Exception e) {
                log.warn("[头像] Mojang 皮肤获取失败 {}: {}", name, e.getMessage());
            }
        }
        // 2. 兜底:程序绘制默认脸
        return defaultFace(name);
    }

    /** 按名查官方 UUID(名字正版匹配);命中 24h/未命中 1h 缓存,网络异常不缓存下次重试 */
    String officialUuidByName(String name) {
        UuidCacheEntry hit = uuidCache.get(name);
        long now = System.currentTimeMillis();
        if (hit != null) {
            long ttl = hit.uuid() != null ? UUID_HIT_TTL_MS : UUID_MISS_TTL_MS;
            if (now - hit.at() < ttl) return hit.uuid();
        }
        String uuid;
        try {
            uuid = fetchOfficialUuid(name);
        } catch (Exception e) {
            log.warn("[头像] Mojang 按名查档失败 {}: {}", name, e.getMessage());
            return null;
        }
        if (uuidCache.size() > UUID_CACHE_CAP) {
            uuidCache.entrySet().removeIf(e -> now - e.getValue().at() > UUID_MISS_TTL_MS);
        }
        uuidCache.put(name, new UuidCacheEntry(uuid, now));
        return uuid;
    }

    /** 实际查档(HTTP);200→官方 UUID(dashless),204/404→null(未命中),其它→抛出。包内可见,单测覆盖 */
    String fetchOfficialUuid(String name) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(MOJANG_API + enc(name)))
                .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            JsonNode id = mapper.readTree(resp.body()).path("id");
            return id.isMissingNode() || id.asText("").isBlank() ? null : id.asText();
        }
        if (resp.statusCode() == 204 || resp.statusCode() == 404) return null;
        throw new IllegalStateException("Mojang API HTTP " + resp.statusCode());
    }

    /** 官方皮肤(按官方 UUID 取 session server 材质) */
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

    private SkinData downloadSkin(String url) throws Exception {
        HttpResponse<byte[]> resp = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) return null;
        return skinFromBytes(resp.body());
    }

    private SkinData skinFromBytes(byte[] png) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        if (img == null || img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) return null;
        return new SkinData(img, sha256Hex(png));
    }

    // ---------- 磁盘持久化 ----------

    private Path diskFile(String name) {
        return cacheDir.resolve(sha256Hex(name.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".png");
    }

    /** 官方皮肤落盘(原子写);默认脸不落盘(程序生成零成本) */
    void persistToDisk(String name, SkinData skin) {
        if (skin.image().getWidth() != 64) return;
        try {
            Files.createDirectories(cacheDir);
            Path target = diskFile(name);
            Path tmp = cacheDir.resolve(target.getFileName() + ".tmp");
            ImageIO.write(skin.image(), "png", tmp.toFile());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            log.warn("[头像] 皮肤落盘失败 {}: {}", name, e.getMessage());
        }
    }

    /** 读盘(损坏/不存在返回 null);ETag 由文件内容重算,跨重启稳定 */
    SkinData loadFromDisk(String name) {
        try {
            Path f = diskFile(name);
            if (!Files.exists(f)) return null;
            return skinFromBytes(Files.readAllBytes(f));
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
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

    private String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
