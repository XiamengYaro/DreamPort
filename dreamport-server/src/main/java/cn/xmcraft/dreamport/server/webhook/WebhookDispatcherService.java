package cn.xmcraft.dreamport.server.webhook;

import cn.xmcraft.dreamport.server.settings.SettingService;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通用事件 Webhook:事件 → 管理员配置的 URL 列表(异步 POST)。
 * - 飞书机器人(open.feishu.cn/open-apis/bot/v2/hook)自动适配:转为飞书 interactive 卡片,
 *   启用签名校验的机器人按飞书算法在请求体顶层带 timestamp/sign(HMAC-SHA256,密钥=ts+"\n"+secret,空消息体)
 * - 其他目标:原样 POST JSON,可选 X-DP-Signature 头(HMAC-SHA256(secret, ts+"."+body))
 * - 失败重试 2 次(1s/3s 退避);最终失败记日志
 */
@Service
public class WebhookDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcherService.class);
    public static final String KEY_WEBHOOK_CONFIG = "webhook.config";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String FEISHU_HOOK_MARKER = "/open-apis/bot/v2/hook";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");

    /** 事件 → 飞书卡片中文标题 */
    private static final Map<String, String> EVENT_TITLES = Map.ofEntries(
            Map.entry("review.approved", "白名单审核通过"),
            Map.entry("review.rejected", "白名单审核驳回"),
            Map.entry("review.banned", "玩家封禁"),
            Map.entry("review.unbanned", "玩家解封"),
            Map.entry("review.deleted", "白名单删除"),
            Map.entry("appeal.approved", "申诉通过"),
            Map.entry("appeal.rejected", "申诉驳回"),
            Map.entry("questionnaire.submitted", "问卷提交"),
            Map.entry("reward.sent", "奖励发放"),
            Map.entry("feedback.created", "工单创建"),
            Map.entry("feedback.replied", "工单回复"),
            Map.entry("feedback.closed", "工单关闭"),
            Map.entry("poll.created", "投票创建"),
            Map.entry("poll.opened", "投票开启"),
            Map.entry("poll.closed", "投票关闭"));

    /** 通用字段 → 中文标签(飞书卡片正文) */
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "operator", "操作人",
            "target", "对象",
            "detail", "详情",
            "username", "玩家",
            "name", "名称",
            "id", "编号",
            "title", "标题");

    private static final Map<String, String> FIELD_ICONS = Map.of(
            "operator", "👤",
            "target", "🎯",
            "detail", "💬");

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

    /** 单次投递结果(测试端点用,同步返回) */
    public record DeliveryResult(String url, boolean ok, int status, String response) {
    }

    /**
     * 分发事件(异步,不阻塞调用方)。
     *
     * @param event 事件类型(如 review.approved / feedback.reply)
     * @param data  事件数据(会与 event/time 一起序列化)
     */
    public void dispatch(String event, Map<String, Object> data) {
        List<Target> targets = resolveTargets(event);
        if (targets.isEmpty()) {
            return;
        }
        long time = System.currentTimeMillis();
        String body = serialize(event, time, data);
        for (Target t : targets) {
            executor.submit(() -> deliver(t, body, 0));
        }
    }

    /** 同步分发(测试端点):逐目标投递并收集真实响应 */
    public List<DeliveryResult> dispatchSync(String event, Map<String, Object> data) {
        List<Target> targets = resolveTargets(event);
        List<DeliveryResult> results = new ArrayList<>();
        long time = System.currentTimeMillis();
        String body = serialize(event, time, data);
        for (Target t : targets) {
            results.add(deliverOnce(t, body, 0));
        }
        return results;
    }

    private List<Target> resolveTargets(String event) {
        Map<String, Object> cfg = settingService.getMap(KEY_WEBHOOK_CONFIG);
        if (!(cfg.getOrDefault("enabled", Boolean.FALSE) instanceof Boolean b) || !b) {
            return List.of();
        }
        List<?> events = cfg.get("events") instanceof List<?> l ? l : List.of();
        if (!events.isEmpty() && !events.contains(event)) {
            return List.of();
        }
        if (!(cfg.get("urls") instanceof List<?> urls) || urls.isEmpty()) {
            return List.of();
        }
        List<Target> targets = new ArrayList<>();
        for (Object o : urls) {
            if (o instanceof Map<?, ?> m && m.get("url") != null) {
                targets.add(new Target(String.valueOf(m.get("url")), m.get("secret") == null ? "" : String.valueOf(m.get("secret"))));
            }
        }
        return targets;
    }

    private String serialize(String event, long time, Map<String, Object> data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", event);
            payload.put("time", time);
            if (data != null) {
                payload.putAll(data);
            }
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("事件序列化失败", e);
        }
    }

    private void deliver(Target target, String body, int attempt) {
        DeliveryResult r = deliverOnce(target, body, attempt);
        if (r.ok() || attempt >= 2) {
            if (!r.ok()) {
                log.warn("Webhook {} 重试耗尽,放弃 (attempt {}): {}", target.url(), attempt,
                        r.response() == null ? "" : r.response().substring(0, Math.min(r.response().length(), 200)));
            }
            return;
        }
        try {
            Thread.sleep(attempt == 0 ? 1000 : 3000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
        }
        deliver(target, body, attempt + 1);
    }

    private DeliveryResult deliverOnce(Target target, String body, int attempt) {
        try {
            long ts = System.currentTimeMillis();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(target.url()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (isFeishu(target.url())) {
                // 飞书自定义机器人:timestamp/sign 放请求体顶层(秒级 ts)
                long tsSec = ts / 1000;
                builder.header("X-DP-Event", "1");
                return postFeishu(target, body, tsSec, attempt);
            }
            if (!target.secret().isBlank()) {
                builder.header("X-DP-Signature", "sha256=" + hmacSha256(target.secret(), ts + "." + body));
                builder.header("X-DP-Timestamp", String.valueOf(ts));
            }
            HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.statusCode() >= 200 && resp.statusCode() < 300;
            return new DeliveryResult(target.url(), ok, resp.statusCode(), resp.body());
        } catch (Exception e) {
            log.warn("Webhook {} 失败 (attempt {}): {}", target.url(), attempt + 1, e.getMessage());
            return new DeliveryResult(target.url(), false, attempt, e.getMessage() == null ? "error" : e.getMessage());
        }
    }

    /** 飞书 hook:转成飞书 interactive 卡片(事件名/字段中文展示,时间格式化) */
    private DeliveryResult postFeishu(Target target, String genericBody, long tsSec, int attempt) {
        try {
            JsonNode generic = mapper.readTree(genericBody);
            String event = generic.path("event").asText("");
            String title = EVENT_TITLES.getOrDefault(event, event.isBlank() ? "事件通知" : event);
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("config", Map.of("wide_screen_mode", true));
            card.put("header", Map.of(
                    "title", Map.of("tag", "plain_text", "content", "DreamPort · " + title),
                    "template", "orange"));
            card.put("elements", List.of(Map.of(
                    "tag", "div",
                    "text", Map.of("tag", "lark_md", "content", buildCardContent(generic)))));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msg_type", "interactive");
            body.put("card", card);
            if (!target.secret().isBlank()) {
                // 飞书签名校验:HMAC-SHA256(密钥 = ts+"\n"+secret, 消息体为空),Base64
                body.put("timestamp", String.valueOf(tsSec));
                body.put("sign", feishuSign(target.secret(), tsSec));
            }
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target.url()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            // 飞书 HTTP 200 但 body.code != 0 视为失败
            JsonNode respNode = mapper.readTree(resp.body() == null || resp.body().isEmpty() ? "{}" : resp.body());
            int code = respNode.path("code").asInt(respNode.path("StatusCode").asInt(0));
            boolean ok = resp.statusCode() == 200 && code == 0;
            if (!ok) {
                log.warn("飞书推送失败 (attempt {}): HTTP {} code {} {}", target.url(), resp.statusCode(), code, resp.body());
            }
            return new DeliveryResult(target.url(), ok, resp.statusCode(), resp.body());
        } catch (Exception e) {
            log.warn("飞书推送异常 (attempt {}): {}", attempt + 1, e.getMessage());
            return new DeliveryResult(target.url(), false, attempt, e.getMessage() == null ? "error" : e.getMessage());
        }
    }

    /** 卡片正文:类型行 + 业务字段(中文标签)+ 格式化时间;跳过 event/time/action 等元字段 */
    static String buildCardContent(JsonNode generic) {
        String event = generic.path("event").asText("");
        String title = EVENT_TITLES.getOrDefault(event, event);
        StringBuilder md = new StringBuilder("📋 **类型**: ").append(title);
        if (!event.isBlank() && !event.equals(title)) {
            md.append(" (`").append(event).append("`)");
        }
        if (generic.isObject()) {
            var fields = generic.fields();
            while (fields.hasNext()) {
                var f = fields.next();
                String key = f.getKey();
                if ("event".equals(key) || "time".equals(key) || "action".equals(key)) {
                    continue;
                }
                String value = f.getValue().asText("").replace("\n", " ");
                md.append('\n').append(FIELD_ICONS.getOrDefault(key, "•")).append(" **")
                        .append(FIELD_LABELS.getOrDefault(key, key)).append("**: ").append(value);
            }
        }
        long ts = generic.path("time").asLong(0);
        if (ts > 0) {
            md.append('\n').append("🕐 **时间**: ")
                    .append(TS_FMT.format(Instant.ofEpochMilli(ts).atZone(CN_ZONE)));
        }
        return md.toString();
    }

    /** 飞书签名:HMAC-SHA256,密钥 = ts + "\n" + secret,内容为空字节 */
    static String feishuSign(String secret, long tsSeconds) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            String stringToSign = tsSeconds + "\n" + secret;
            mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(new byte[]{});
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            throw new IllegalStateException("飞书签名计算失败", e);
        }
    }

    static boolean isFeishu(String url) {
        return url != null && url.contains(FEISHU_HOOK_MARKER);
    }

    private String hmacSha256(String secret, String content) {
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
