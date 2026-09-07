package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.blessingskin.BlessingSkinService;
import cn.xmcraft.dreamport.server.config.WlProps;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.security.TokenService;
import cn.xmcraft.dreamport.server.settings.SystemSettingsService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BlessingSkin 互通（DreamPort 作 OAuth2 Provider，皮肤站用本站账号登录）。
 *
 * 授权码流程：
 * 1. BS 插件把用户浏览器重定向到本站 /oauth2/authorize（SPA 路由）→ 前端查 /api/oauth2/authorize-info 展示确认卡
 * 2. 用户确认 → POST /api/oauth2/authorize 签发一次性授权码（5 分钟 TTL）→ 前端带 code 跳回 BS 回调
 * 3. BS 插件服务端 POST /oauth2/token 用 code 换 access_token（即本站 JWT）
 * 4. BS 插件 GET /oauth2/userinfo 取 username/email → 按 email 关联或注册皮肤站账号
 *
 * 配置：Admin「系统设置 → BlessingSkin 互通」（dp_setting blessingskin.*）。
 */
@RestController
public class OAuth2Controller {

    private static final long CODE_TTL_MS = 5 * 60 * 1000L;
    /** 授权回调路径（与 BS 端插件约定，redirect_uri 必须等于 {bs.url}{CALLBACK_PATH}） */
    public static final String CALLBACK_PATH = "/auth/login/dreamport/callback";

    private final SystemSettingsService settingsService;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final BlessingSkinService blessingSkinService;
    private final long tokenTtlSeconds;
    private final SecureRandom random = new SecureRandom();

    private record CodeEntry(String username, String clientId, String redirectUri, long expiresAt) {}

    private final ConcurrentHashMap<String, CodeEntry> codes = new ConcurrentHashMap<>();

    public OAuth2Controller(SystemSettingsService settingsService, TokenService tokenService,
                            UserRepository userRepository, BlessingSkinService blessingSkinService,
                            WlProps props) {
        this.settingsService = settingsService;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.blessingSkinService = blessingSkinService;
        this.tokenTtlSeconds = props.security().jwtTtlDays() * 86_400L;
    }

    // ---------- 用户侧：授权确认 ----------

    @GetMapping("/api/oauth2/authorize-info")
    public ResponseEntity<Object> authorizeInfo(@RequestParam("client_id") String clientId,
                                                @RequestParam("redirect_uri") String redirectUri,
                                                HttpServletRequest request) {
        if (AuthUtil.currentUser(request) == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        String error = validate(clientId, redirectUri);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("clientName", "BlessingSkin 皮肤站");
        data.put("valid", error == null);
        if (error != null) data.put("reason", error);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @PostMapping("/api/oauth2/authorize")
    public ResponseEntity<Object> authorize(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String username = AuthUtil.currentUser(request);
        if (username == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        if (!Boolean.TRUE.equals(body.get("approved"))) {
            return ResponseEntity.ok(ApiResponse.success("已取消授权", Map.of("redirectUrl", "")));
        }
        String clientId = str(body.get("clientId"));
        String redirectUri = str(body.get("redirectUri"));
        String state = str(body.get("state"));
        String error = validate(clientId, redirectUri);
        if (error != null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(error));
        }
        purgeExpiredCodes();
        String code = newToken();
        codes.put(code, new CodeEntry(username, clientId, redirectUri, System.currentTimeMillis() + CODE_TTL_MS));
        StringBuilder url = new StringBuilder(redirectUri)
                .append(redirectUri.contains("?") ? '&' : '?')
                .append("code=").append(urlEncode(code));
        if (!state.isBlank()) {
            url.append("&state=").append(urlEncode(state));
        }
        return ResponseEntity.ok(ApiResponse.success("授权成功", Map.of("redirectUrl", url.toString())));
    }

    // ---------- 服务端：BS 插件调用 ----------

    @PostMapping("/oauth2/token")
    public ResponseEntity<Object> token(@RequestParam Map<String, String> form) {
        var cfg = settingsService.blessingskinConfig();
        if (error(cfg, null, null) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "server_not_configured"));
        }
        if (!"authorization_code".equals(form.get("grant_type"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }
        String clientId = str(form.get("client_id"));
        String clientSecret = str(form.get("client_secret"));
        String redirectUri = str(form.get("redirect_uri"));
        String code = str(form.get("code"));
        if (!constEq(clientId, String.valueOf(cfg.get("clientId")))
                || !constEq(clientSecret, String.valueOf(cfg.get("clientSecret")))) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_client"));
        }
        // 单次消费：先取走再校验，防止重放
        CodeEntry entry = codes.remove(code);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()
                || !entry.clientId().equals(clientId) || !entry.redirectUri().equals(redirectUri)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_grant"));
        }
        String accessToken = tokenService.issue(entry.username(), TokenService.ROLE_USER);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", accessToken);
        body.put("token_type", "Bearer");
        body.put("expires_in", tokenTtlSeconds);
        body.put("scope", "");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/oauth2/userinfo")
    public ResponseEntity<Object> userinfo(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_token"));
        }
        Claims claims = tokenService.parse(header.substring(7).trim());
        if (claims == null) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_token"));
        }
        UserRecord user = userRepository.findByUsernameIgnoreCase(claims.getSubject()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_token"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", user.username());
        data.put("email", user.email() == null ? "" : user.email());
        data.put("nickname", user.minecraftName() == null || user.minecraftName().isBlank()
                ? user.username() : user.minecraftName());
        data.put("minecraftName", user.minecraftName());
        data.put("minecraftUuid", user.minecraftUuid());
        return ResponseEntity.ok(data);
    }

    // ---------- 用户侧：查看自己在皮肤站的角色 ----------

    @GetMapping("/api/user/bs/players")
    public ResponseEntity<Object> myBsPlayers(HttpServletRequest request) {
        String username = AuthUtil.currentUser(request);
        if (username == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        UserRecord user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("用户不存在"));
        }
        var cfg = settingsService.blessingskinConfig();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", Boolean.TRUE.equals(cfg.get("enabled"))
                && !str(cfg.get("url")).isBlank() && !str(cfg.get("apiSecret")).isBlank());
        data.put("bsUrl", str(cfg.get("url")).replaceAll("/+$", ""));
        if (Boolean.FALSE.equals(data.get("configured")) || user.email() == null || user.email().isBlank()) {
            data.put("linked", false);
            data.put("players", java.util.List.of());
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        }
        try {
            var players = blessingSkinService.fetchPlayers(str(cfg.get("url")), str(cfg.get("apiSecret")), user.email());
            data.put("linked", players != null);
            data.put("players", players == null ? java.util.List.of() : players);
        } catch (Exception e) {
            data.put("linked", false);
            data.put("players", java.util.List.of());
            data.put("error", "皮肤站接口暂不可用");
        }
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    // ---------- 内部工具 ----------

    /** 校验 client 与 redirect_uri；返回 null 表示通过 */
    private String validate(String clientId, String redirectUri) {
        return error(settingsService.blessingskinConfig(), clientId, redirectUri);
    }

    private String error(Map<String, Object> cfg, String clientId, String redirectUri) {
        if (!Boolean.TRUE.equals(cfg.get("enabled"))) {
            return "BlessingSkin 互通未启用";
        }
        String url = str(cfg.get("url"));
        if (url.isBlank() || str(cfg.get("clientId")).isBlank() || str(cfg.get("clientSecret")).isBlank()) {
            return "BlessingSkin 互通配置不完整";
        }
        if (clientId != null && !constEq(clientId, String.valueOf(cfg.get("clientId")))) {
            return "client_id 不匹配";
        }
        if (redirectUri != null && !redirectUri.equals(expectedRedirectUri(url))) {
            return "redirect_uri 不受支持";
        }
        return null;
    }

    private String expectedRedirectUri(String bsUrl) {
        return bsUrl.replaceAll("/+$", "") + CALLBACK_PATH;
    }

    private void purgeExpiredCodes() {
        long now = System.currentTimeMillis();
        codes.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private boolean constEq(String a, String b) {
        return MessageDigest.isEqual(
                (a == null ? "" : a).getBytes(StandardCharsets.UTF_8),
                (b == null ? "" : b).getBytes(StandardCharsets.UTF_8));
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
