package cn.xmcraft.dreamport.server.questionnaire;

import cn.xmcraft.dreamport.server.config.WlProps;
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
 * LLM 评分客户端（OpenAI 兼容 /chat/completions，吸收自 参考项目 的评分抽象——吸收项 3-15）。
 * 结果携带 confidence 与 manualReview 标记（置信度 <0.6 转人工复核队列），
 * 并附 provider/model/latency 观测字段；熔断：连续 5 次失败开 30 秒（对齐旧版参数）。
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

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final WlProps props;
    private final Semaphore permits;

    private int consecutiveFailures;
    private long openUntil;

    public LlmScoringClient(WlProps props) {
        this.props = props;
        this.permits = new Semaphore(Math.max(1, props.llm().maxConcurrency()));
    }

    public boolean enabled() {
        return props.llm().enabled() && props.llm().apiKey() != null && !props.llm().apiKey().isBlank();
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
        if (!permits.tryAcquire()) {
            return ScoringResult.unavailable();
        }
        long start = System.currentTimeMillis();
        try {
            String userPrompt = "评分规则：" + nullSafe(scoringRule) + "\n问题：" + nullSafe(question)
                    + "\n回答：" + truncate(nullSafe(answer), 2000)
                    + "\n满分：" + maxScore
                    + "\n请仅返回 JSON：{\"score\": 数字, \"reason\": \"评语\", \"confidence\": 0到1}";
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(props.llm().apiBase() + "/chat/completions"))
                    .timeout(Duration.ofMillis(props.llm().timeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.llm().apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                            "model", props.llm().model(),
                            "messages", java.util.List.of(
                                    Map.of("role", "system", "content", props.llm().systemPrompt()),
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
                    confidence < 0.6, "openai-compatible", props.llm().model(), latency, 0);
        } catch (Exception e) {
            recordFailure();
            log.warn("LLM 评分失败: {}", e.getMessage());
            return ScoringResult.unavailable();
        } finally {
            permits.release();
        }
    }

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
