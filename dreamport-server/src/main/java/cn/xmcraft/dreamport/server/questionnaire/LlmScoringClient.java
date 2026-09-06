package cn.xmcraft.dreamport.server.questionnaire;

import cn.xmcraft.dreamport.server.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * LLM 评分客户端（OpenAI 兼容 /chat/completions，评分结果带置信度与人工复核标记）。
 * 结果携带 confidence 与 manualReview 标记（置信度 <0.6 转人工复核队列），
 * 并附 provider/model/latency 观测字段；熔断：连续 5 次失败开 30 秒（对齐旧版参数）。
 * 配置来源：dp_setting llm.config（管理面板「AI 评分设置」，热生效）。
 */
@Component
public class LlmScoringClient {

    private static final Logger log = LoggerFactory.getLogger(LlmScoringClient.class);
    private static final int FAILURE_THRESHOLD = 5;
    private static final long OPEN_MS = 30_000;

    public record ScoringResult(int score, String reason, double confidence, boolean manualReview,
                                String provider, String model, long latencyMs, int retryCount) {
        public static ScoringResult unavailable() {
            return new ScoringResult(-1, "LLM 服务暂不可用", 0, true, "llm", "", 0, 0);
        }
    }

    private final SystemSettingsService systemSettings;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private Semaphore permits;

    private int consecutiveFailures;
    private long openUntil;

    public LlmScoringClient(SystemSettingsService systemSettings) {
        this.systemSettings = systemSettings;
        this.permits = new Semaphore(Math.max(1, maxConcurrency()));
    }

    private Map<String, Object> config() {
        return systemSettings.llmConfig();
    }

    public int maxConcurrency() {
        return ((Number) config().getOrDefault("maxConcurrency", 4)).intValue();
    }

    public boolean enabled() {
        var c = config();
        boolean enabled = Boolean.TRUE.equals(c.get("enabled"));
        String apiKey = String.valueOf(c.getOrDefault("apiKey", ""));
        return enabled && !apiKey.isBlank() && !"***".equals(apiKey);
    }

    public boolean circuitOpen() {
        return System.currentTimeMillis() < openUntil;
    }

    /** 评分：返回 0..maxScore 分值；失败/熔断返回 manualReview 结果 */
    public ScoringResult score(String question, String answer, String scoringRule, int maxScore) {
        if (!enabled()) {
            return ScoringResult.unavailable();
        }
        if (circuitOpen()) {
            return ScoringResult.unavailable();
        }
        Semaphore semaphore = permits();
        if (!semaphore.tryAcquire()) {
            return ScoringResult.unavailable();
        }
        long start = System.currentTimeMillis();
        try {
            var c = config();
            String apiBase = String.valueOf(c.getOrDefault("apiBase", ""));
            String apiKey = String.valueOf(c.getOrDefault("apiKey", ""));
            String model = String.valueOf(c.getOrDefault("model", ""));
            long timeoutMs = ((Number) c.getOrDefault("timeoutMs", 10_000)).longValue();
            String systemPrompt = String.valueOf(c.getOrDefault("systemPrompt", ""));

            String userPrompt = "评分规则：" + nullSafe(scoringRule) + "\n问题：" + nullSafe(question)
                    + "\n回答：" + truncate(nullSafe(answer), 2000)
                    + "\n满分：" + maxScore
                    + "\n请仅返回 JSON：{\"score\": 数字, \"reason\": \"评语\", \"confidence\": 0到1}";
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/chat/completions"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                            "model", model,
                            "messages", java.util.List.of(
                                    Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt)),
                            "temperature", 0.2))))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            if (response.statusCode() != 200) {
                recordFailure();
                return ScoringResult.unavailable();
            }
            JsonNode root = mapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode parsed = mapper.readTree(extractJson(content));
            int score = (int) Math.round(parsed.path("score").asDouble(-1) * maxScore / 10.0);
            if (score < 0) {
                recordFailure();
                return ScoringResult.unavailable();
            }
            score = Math.min(score, maxScore);
            double confidence = Math.max(0, Math.min(1, parsed.path("confidence").asDouble(0.5)));
            consecutiveFailures = 0;
            return new ScoringResult(score, parsed.path("reason").asText(""), confidence,
                    confidence < 0.6, "openai-compatible", model, latency, 0);
        } catch (Exception e) {
            recordFailure();
            log.warn("LLM 评分失败: {}", e.getMessage());
            return ScoringResult.unavailable();
        } finally {
            semaphore.release();
        }
    }

    /** 面板可改并发数：配置变化时重建信号量 */
    private Semaphore permits() {
        int max = Math.max(1, maxConcurrency());
        if (lastMax != max) {
            synchronized (this) {
                if (lastMax != max) {
                    permits = new Semaphore(max);
                    lastMax = max;
                }
            }
        }
        return permits;
    }

    private volatile int lastMax;

    private void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            openUntil = System.currentTimeMillis() + OPEN_MS;
            consecutiveFailures = 0;
            log.warn("LLM 连续失败 {} 次，熔断 {}ms", FAILURE_THRESHOLD, OPEN_MS);
        }
    }

    /** 容忍模型输出 ```json 包裹 */
    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        return start >= 0 && end > start ? trimmed.substring(start, end + 1) : trimmed;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
