package cn.xmcraft.dreamport.server.security;

import cn.xmcraft.dreamport.server.config.WlProps;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.stats.ServerStatsService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 服务器通道鉴权(从 InternalController 抽出共用,供多个 internal 控制器使用):
 * - shared 模式(默认):全局 wl.internal.server-token 单令牌
 * - per_server 模式:按 X-Server-Id 查 dp_server.token_hash(SHA-256)比对且 enabled=TRUE;
 *   全局令牌保留为应急通道(break-glass,命中记 warn 便于审计)
 */
@Service
public class ServerTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(ServerTokenVerifier.class);

    private final WlProps props;
    private final SettingService settingService;
    private final ServerStatsService statsService;

    public ServerTokenVerifier(WlProps props, SettingService settingService, ServerStatsService statsService) {
        this.props = props;
        this.settingService = settingService;
        this.statsService = statsService;
    }

    /** @return true 鉴权通过;false 应返回 401 */
    public boolean verify(HttpServletRequest request) {
        String token = request.getHeader(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_TOKEN);
        if (token == null || token.isBlank()) {
            return false;
        }
        String global = props.internal().serverToken();
        if (global != null && !global.isBlank() && global.equals(token)) {
            if ("per_server".equals(tokenMode())) {
                log.warn("[鉴权] {} 使用全局共享令牌(per_server 模式应急通道)",
                        request.getHeader(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_ID));
            }
            return true;
        }
        if (!"per_server".equals(tokenMode())) {
            return false;
        }
        String serverId = request.getHeader(cn.xmcraft.dreamport.common.Protocol.HEADER_SERVER_ID);
        if (serverId == null || serverId.isBlank()) {
            return false;
        }
        String expected = statsService.tokenHashOf(serverId);
        return expected != null && sha256Matches(token, expected);
    }

    private String tokenMode() {
        var cfg = settingService.get(SettingService.KEY_SECURITY_CONFIG, java.util.Map.class);
        Object mode = cfg == null ? null : cfg.get("tokenMode");
        return mode == null ? "shared" : String.valueOf(mode);
    }

    /** 恒时比较 SHA-256(令牌) 与库中哈希 */
    private static boolean sha256Matches(String token, String expectedHex) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return MessageDigest.isEqual(
                    hex.toString().getBytes(StandardCharsets.UTF_8),
                    expectedHex.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
