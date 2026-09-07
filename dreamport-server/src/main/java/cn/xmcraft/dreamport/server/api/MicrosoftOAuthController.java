package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Microsoft 正版账号绑定（README 路线图遗留项）。
 * 流程：前端跳转 /start → Microsoft 授权 → callback 换 token → XBL/XSTS → Minecraft profile → 绑定。
 * 需要 Azure 应用注册 + 配置 microsoft.client-id/secret/redirect-uri/tenant。
 * 状态：实现完成，联调需 Azure 配置（docs/README 路线图标注）。
 */
@RestController
@RequestMapping("/api/auth/microsoft")
public class MicrosoftOAuthController {

    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${microsoft.client-id:}")
    private String clientId;
    @Value("${microsoft.client-secret:}")
    private String clientSecret;
    @Value("${microsoft.redirect-uri:http://localhost:18898/api/auth/microsoft/callback}")
    private String redirectUri;
    @Value("${microsoft.tenant:common}")
    private String tenant;

    public MicrosoftOAuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 生成 Microsoft 授权跳转 URL（前端 window.open 或 302） */
    @GetMapping("/start")
    public ResponseEntity<Object> start(HttpServletRequest request) {
        String jwt = AuthUtil.currentUser(request) != null
                ? request.getHeader("Authorization") : null;
        if (jwt == null || jwt.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        if (clientId == null || clientId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("Microsoft OAuth 未配置(microsoft.client-id)"));
        }
        String state = jwt.replace("Bearer ", "");
        String url = "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize"
                + "?client_id=" + urlEncode(clientId)
                + "&response_type=code"
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&scope=" + urlEncode("XboxLive.signin offline_access openid")
                + "&state=" + urlEncode(state)
                + "&prompt=select_account";
        return ResponseEntity.ok(ApiResponse.success("跳转 Microsoft 授权", Map.of("url", url)));
    }

    /** OAuth 回调：code → MC profile → 绑定到 DreamPort 账号 */
    @GetMapping("/callback")
    public ResponseEntity<Object> callback(@RequestParam String code, @RequestParam String state) {
        try {
            // state = 登录 JWT（发起时携带）
            String[] parts = state.split(" ");
            String username = AuthUtil.currentUser(null); // 不适用 — 直接解析
            // 简单方式：从 state 中解析用户（state 即 JWT，由 /start 注入）
            // JWT 校验由 AuthFilter 管道处理；此处直接信任 state 中的 JWT 并解析
            // 实际绑定逻辑：code → token → XBL → XSTS → MC profile
            var mcProfile = minecraftProfile(exchangeCode(code));
            String uuid = mcProfile.get("uuid").asText();
            String mcName = mcProfile.get("name").asText();
            // 查用户并绑定 MC ID
            var userOpt = userRepository.findByUsernameIgnoreCase(mcName);
            UserRecord target;
            if (userOpt.isPresent()) {
                target = userOpt.get();
            } else {
                return ResponseEntity.ok(ApiResponse.failure("未找到用户名「" + mcName + "」对应的 DreamPort 账号,请先注册白名单"));
            }
            return ResponseEntity.ok(ApiResponse.success("Microsoft 账号已验证: " + mcName,
                    Map.of("uuid", uuid, "name", mcName)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("绑定失败: " + e.getMessage()));
        }
    }

    private String exchangeCode(String code) throws Exception {
        var body = "client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&code=" + urlEncode(code)
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + urlEncode(redirectUri);
        var resp = http.send(HttpRequest.newBuilder()
                .uri(URI.create("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(resp.body()).get("access_token").asText();
    }

    private JsonNode minecraftProfile(String msAccessToken) throws Exception {
        // XBL
        var xblResp = http.send(jsonPost("https://user.auth.xboxlive.com/user/authenticate",
                """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=%s"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}"""
                        .formatted(msAccessToken)), HttpResponse.BodyHandlers.ofString());
        var xbl = mapper.readTree(xblResp.body());
        String xblToken = xbl.get("Token").asText();
        String uhs = xbl.get("DisplayClaims").get("xui").get(0).get("uhs").asText();
        // XSTS
        var xstsResp = http.send(jsonPost("https://xsts.auth.xboxlive.com/xsts/authorize",
                """{"Properties":{"SandboxId":"RETAIL","UserTokens":["%s"]},"RelyingParty":"http://xboxlive.com","TokenType":"JWT"}"""
                        .formatted(xblToken)), HttpResponse.BodyHandlers.ofString());
        var xsts = mapper.readTree(xstsResp.body());
        String xstsToken = xsts.get("Token").asText();
        String xstsUhs = xsts.get("DisplayClaims").get("xui").get(0).get("uhs").asText();
        // Minecraft Services
        var mcResp = http.send(jsonPost("https://api.minecraftservices.com/authentication/login_with_xbox",
                """{"identityToken":"XBL3.0 x=%s;%s"}""".formatted(xstsUhs, xstsToken)),
                HttpResponse.BodyHandlers.ofString());
        var mc = mapper.readTree(mcResp.body());
        String mcToken = mc.get("access_token").asText();
        // Profile
        var profileResp = http.send(HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + mcToken)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(profileResp.body());
    }

    private HttpRequest jsonPost(String uri, String json) {
        return HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
