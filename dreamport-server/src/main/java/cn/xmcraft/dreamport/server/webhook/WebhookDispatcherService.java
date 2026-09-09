package cn.xmcraft.dreamport.server.webhook;

import cn.xmcraft.dreamport.server.settings.SettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通用事件 Webhook:事件 → 管理员配置的 URL 列表(异步 POST + HMAC-SHA256 签名)。
 * 事件源:AuditService.log 统一骨架(action 前缀映射事件类型)。
 * 失败重试 2 次(1s/3s 退避),最终失败记日志——不落表,MVP 以日志排查为主。
 */
@Service
public class WebhookDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcherService.class);
    public static final String KEY_WEBHOOK_CONFIG = "webhook.config";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final SettingService settingService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "webhook-dispatch");
        t.setDaemon(true);
        return t;
    });

    public WebhookDispatcherService(SettingService settingService) {
        this.settingService = settingService;
    }

    public record Target(String url, String secret) {
    }

    /**
     * 分发事件(异步,不阻塞调用方)。
     * @param event 事件类型(如 review.approved / feedback.reply)
     * @param data  事件数据(会与 event/time 一起序列化)
     */
    public void dispatch(String event, Map<String, Object> data) {
        try {
            Map<String, Object> cfg = settingService.getMap(KEY_WEBHOOK_CONFIG);
            if (!(cfg.getOrDefault("enabled", Boolean.FALSE) instanceof Boolean b) || !b) {
                return;
            }
            List<?> events = cfg.get("events") instanceof List<?> l ? l : List.of();
            if (!events.isEmpty() && !events.contains(event)) {
                return;
            }
            if (!(cfg.get("urls") instanceof List<?> urls) || urls.isEmpty()) {
                return;
            }
            long time = System.currentTimeMillis();
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("event", event);
            payload.put("time", time);
            if (data != null) {
                payload.putAll(data);
            }
            String body;
            try {
                body = mapper.writeValueAsString(payload);
            } catch (Exception e) {
                return;
            }
            for (Object o : urls) {
                if (o instanceof Map<?, ?> m && m.get("url") != null) {
                    String url = String.valueOf(m.get("url"));
                    String secret = m.get("secret") == null ? "" : String.valueOf(m.get("secret"));
                    executor.submit(() -> deliver(new Target(url, secret), body, 0));
                }
            }
        } catch (Exception e) {
            log.warn("Webhook 分发异常: {}", e.getMessage());
        }
    }

    private void deliver(Target target, String body, int attempt) {
        try {
            long ts = System.currentTimeMillis();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(target.url()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-DP-Event", "1")
                    .header("X-DP-Timestamp", String.valueOf(ts))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!target.secret().isBlank()) {
                builder.header("X-DP-Signature", "sha256=" + hmacSha256(target.secret(), ts + "." + body));
            }
            HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return;
            }
            log.warn("Webhook {} 返回 {} (attempt {})", target.url(), resp.statusCode(), attempt + 1);
        } catch (Exception e) {
            log.warn("Webhook {} 失败 (attempt {}): {}", target.url(), attempt + 1, e.getMessage());
        }
        if (attempt < 2) {
            try {
                Thread.sleep(attempt == 0 ? 1000 : 3000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            deliver(target, body, attempt + 1);
        } else {
            log.warn("Webhook {} 重试耗尽,放弃: {}", target.url(), body.length() > 200 ? body.substring(0, 200) + "…" : body);
        }
    }

    private static String hmacSha256(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 不可用", e);
        }
    }
}
