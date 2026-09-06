package cn.xmcraft.dreamport.server.questionnaire;

import cn.xmcraft.dreamport.server.audit.AuditService;

import cn.xmcraft.dreamport.server.infra.MailService;
import cn.xmcraft.dreamport.server.review.ReviewService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.regex.Pattern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问卷域：题库管理（DB 存储 + 旧版 questionnaire.yml 导入导出）、评分（客观题计分 + LLM/长度降级）、
 * 结果落库与邮件（对齐旧版 QuestionnaireService；SSE 统一单路径——Rules.md §9-3）。
 */
@Service
public class QuestionnaireService {

    private static final Logger log = LoggerFactory.getLogger(QuestionnaireService.class);

    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final LlmScoringClient llmClient;
    private final UserRepository userRepository;
    private final ReviewService reviewService;
    private final AuditService auditService;
    private final MailService mailService;
    private final cn.xmcraft.dreamport.server.settings.SystemSettingsService systemSettings;
    private final cn.xmcraft.dreamport.server.settings.SettingService settingService;

    public QuestionnaireService(QuestionnaireRepository questionnaireRepository,
                                QuestionRepository questionRepository,
                                QuestionOptionRepository optionRepository,
                                LlmScoringClient llmClient, UserRepository userRepository,
                                ReviewService reviewService, AuditService auditService,
                                MailService mailService,
                                cn.xmcraft.dreamport.server.settings.SettingService settingService,
                                cn.xmcraft.dreamport.server.settings.SystemSettingsService systemSettings) {
        this.questionnaireRepository = questionnaireRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.llmClient = llmClient;
        this.userRepository = userRepository;
        this.reviewService = reviewService;
        this.auditService = auditService;
        this.mailService = mailService;
        this.systemSettings = systemSettings;
        this.settingService = settingService;
    }

    /** 活动问卷（无则自动创建默认问卷） */
    public synchronized QuestionnaireRecords.Questionnaire activeQuestionnaire() {
        var list = questionnaireRepository.findByEnabledTrue();
        if (!list.isEmpty()) {
            return list.get(0);
        }
        QuestionnaireRecords.Questionnaire q = new QuestionnaireRecords.Questionnaire(
                null, "默认问卷", true, defaultPassScore(), null);
        return questionnaireRepository.save(q);
    }

    public List<QuestionnaireRecords.Question> questions(long questionnaireId) {
        return questionRepository.findByQuestionnaireIdOrderBySortOrderAsc(questionnaireId);
    }

    public List<QuestionnaireRecords.QuestionOption> options(long questionId) {
        return optionRepository.findByQuestionIdOrderBySortOrderAsc(questionId);
    }

    /** 默认及格分（dp_setting questionnaire.config，热生效） */
    public int defaultPassScore() {
        return ((Number) systemSettings.questionnaireConfig().getOrDefault("passScore", 60)).intValue();
    }

    public boolean enabled() {
        return Boolean.TRUE.equals(systemSettings.questionnaireConfig()
                .getOrDefault("enabled", true));
    }

    public record QuestionResult(long questionId, String questionText, int score, int maxScore,
                                 String reason, boolean manualReview) {
    }

    public record SubmitOutcome(int totalScore, int maxScore, boolean passed,
                                List<QuestionResult> results, String overallSummary) {
    }

    /** 提交评分：answers 键=题目 id，值=选项文本/文本答案 */
    public SubmitOutcome submit(String username, Map<String, Object> answers, String lang) {
        QuestionnaireRecords.Questionnaire questionnaire = activeQuestionnaire();
        List<QuestionnaireRecords.Question> questions = questions(questionnaire.id());
        int total = 0;
        int maxTotal = 0;
        List<QuestionResult> results = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        for (QuestionnaireRecords.Question q : questions) {
            maxTotal += q.maxScore();
            Object raw = answers.get(String.valueOf(q.id()));
            String answer = raw == null ? "" : String.valueOf(raw);
            int score;
            String reason;
            boolean manualReview = false;
            if ("fill_blank".equals(q.type())) {
                // 填空：按 scoring_rule 自动识别（qq/email/phone/number/regex:...），命中得满分
                if (matchesValidation(answer, q.scoringRule())) {
                    score = q.maxScore();
                    reason = "格式正确";
                } else {
                    score = 0;
                    reason = answer == null || answer.isBlank() ? "未作答" : "格式不符合要求";
                }
                total += score;
                results.add(new QuestionResult(q.id(), "zh".equals(lang) ? q.questionZh() : orEn(q),
                        score, q.maxScore(), reason, false));
                summaryLines.add("Q" + q.id() + " " + answer + " → " + score + "/" + q.maxScore());
                continue;
            }
            if ("text".equals(q.type()) || "essay".equals(q.type())) {
                if (llmClient.enabled() && q.scoringRule() != null && !q.scoringRule().isBlank()) {
                    LlmScoringClient.ScoringResult llm = llmClient.score(
                            "zh".equals(lang) ? q.questionZh() : orEn(q), answer, q.scoringRule(), q.maxScore());
                    if (llm.score() >= 0) {
                        score = llm.score();
                        reason = llm.reason() + (llm.manualReview() ? "（低置信度，建议人工复核）" : "");
                        manualReview = llm.manualReview();
                    } else {
                        score = lengthFallback(answer, q.maxScore());
                        reason = lengthReason(answer);
                    }
                } else {
                    score = lengthFallback(answer, q.maxScore());
                    reason = lengthReason(answer);
                }
            } else {
                // 客观题：选项文本精确匹配计分（多选累加），clamp 到 [0, maxScore]
                int gained = 0;
                for (QuestionnaireRecords.QuestionOption opt : options(q.id())) {
                    String text = "en".equals(lang) && opt.textEn() != null ? opt.textEn() : opt.textZh();
                    if (answer.contains(text)) {
                        gained += opt.score();
                    }
                }
                score = Math.max(0, Math.min(gained, q.maxScore()));
                reason = score >= q.maxScore() ? "回答正确" : "回答部分正确/错误";
            }
            total += score;
            results.add(new QuestionResult(q.id(), "zh".equals(lang) ? q.questionZh() : orEn(q),
                    score, q.maxScore(), reason, manualReview));
            summaryLines.add("Q" + q.id() + " " + answer + " → " + score + "/" + q.maxScore());
        }
        boolean passed = maxTotal > 0 && total * 100 / maxTotal >= questionnaire.passScore();
        String overall = "总分 " + total + "/" + maxTotal + (passed ? "，通过" : "，未通过")
                + "；" + String.join("；", summaryLines);
        saveResult(username, questionnaire, total, maxTotal, passed, results, overall, answers);
        return new SubmitOutcome(total, maxTotal, passed, results, overall);
    }

    /** 单题/部分题预评分（SSE 逐题推送用，不落库） */
    public QuestionResult previewScore(Map<String, Object> answers, String lang) {
        QuestionnaireRecords.Questionnaire questionnaire = activeQuestionnaire();
        List<QuestionnaireRecords.Question> questions = questions(questionnaire.id());
        for (QuestionnaireRecords.Question q : questions) {
            if (answers.containsKey(String.valueOf(q.id()))) {
                Object raw = answers.get(String.valueOf(q.id()));
                String answer = raw == null ? "" : String.valueOf(raw);
                if ("fill_blank".equals(q.type())) {
                    boolean ok = matchesValidation(answer, q.scoringRule());
                    return new QuestionResult(q.id(), q.questionZh(), ok ? q.maxScore() : 0, q.maxScore(),
                            ok ? "格式正确" : (answer.isBlank() ? "未作答" : "格式不符合要求"), false);
                }
                if (!"text".equals(q.type()) && !"essay".equals(q.type())) {
                    int gained = 0;
                    for (QuestionnaireRecords.QuestionOption opt : options(q.id())) {
                        String text = "en".equals(lang) && opt.textEn() != null ? opt.textEn() : opt.textZh();
                        if (answer.contains(text)) {
                            gained += opt.score();
                        }
                    }
                    int score = Math.max(0, Math.min(gained, q.maxScore()));
                    return new QuestionResult(q.id(), q.questionZh(), score, q.maxScore(),
                            score >= q.maxScore() ? "回答正确" : "回答部分正确/错误", false);
                }
                if (llmClient.enabled() && q.scoringRule() != null && !q.scoringRule().isBlank()) {
                    LlmScoringClient.ScoringResult llm = llmClient.score(q.questionZh(), answer,
                            q.scoringRule(), q.maxScore());
                    if (llm.score() >= 0) {
                        return new QuestionResult(q.id(), q.questionZh(), llm.score(), q.maxScore(),
                                llm.reason() + (llm.manualReview() ? "（低置信度，建议人工复核）" : ""),
                                llm.manualReview());
                    }
                }
                return new QuestionResult(q.id(), q.questionZh(), lengthFallback(answer, q.maxScore()),
                        q.maxScore(), lengthReason(answer), false);
            }
        }
        return new QuestionResult(-1, "未知题目", 0, 0, "题目不存在", false);
    }

    private static final Pattern QQ_PATTERN = Pattern.compile("^\\d{5,12}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d+$");

    /**
     * 填空题自动识别：scoring_rule 支持 qq / email / phone / number / regex:自定义 / 留空(任意非空)
     */
    public boolean matchesValidation(String answer, String rule) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String r = rule == null ? "" : rule.trim().toLowerCase();
        if (r.startsWith("regex:")) {
            return answer.matches(r.substring(6));
        }
        if (r.contains("qq")) {
            return QQ_PATTERN.matcher(answer).matches();
        }
        if (r.contains("邮箱") || r.contains("email")) {
            return EMAIL_PATTERN.matcher(answer).matches();
        }
        if (r.contains("手机")) {
            return PHONE_PATTERN.matcher(answer).matches();
        }
        if (r.contains("数字") || r.contains("number")) {
            return NUMBER_PATTERN.matcher(answer).matches();
        }
        return true; // 无规则 = 填写即得分
    }

    private String orEn(QuestionnaireRecords.Question q) {
        return q.questionEn() != null ? q.questionEn() : q.questionZh();
    }

    /** 长度降级评分（LLM 未启用/失败时，对齐旧版分档） */
    private int lengthFallback(String answer, int maxScore) {
        int len = answer == null ? 0 : answer.length();
        if (len < 5) return maxScore / 5;
        if (len < 20) return maxScore / 3;
        if (len < 50) return maxScore / 2;
        if (len < 100) return (int) (maxScore * 0.7);
        return maxScore;
    }

    private String lengthReason(String answer) {
        int len = answer == null ? 0 : answer.length();
        if (len < 5) return "回答过短";
        if (len < 50) return "回答较简略";
        return "回答完整";
    }

    private void saveResult(String username, QuestionnaireRecords.Questionnaire questionnaire,
                            int total, int maxTotal, boolean passed, List<QuestionResult> results,
                            String overall, Map<String, Object> answers) {
        var userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            log.warn("问卷提交者不存在: {}", username);
            return;
        }
        UserRecord user = userOpt.get();
        UserRecord updated = new UserRecord(user.id(), user.username(), user.email(),
                passed ? "pending_review" : "rejected",
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), total, passed, overall,
                System.currentTimeMillis(), results.toString(), safeJson(answers),
                user.minecraftUuid(), user.minecraftName(), user.microsoftVerified(),
                user.verifiedAt(), user.verifyType(), user.invitedBy(), user.bedrockUuid(),
                user.bedrockName(), user.bedrockVerified(), user.bedrockVerifiedAt(),
                user.banReason(), user.banTime(), user.avatar());
        userRepository.save(updated);
        auditService.log("questionnaire_submit", username, username,
                total + "/" + maxTotal + (passed ? " passed" : " failed"));
        // 管理员通知邮件（配置化收件人——修复旧版硬编码，Rules.md §9-1）
        String notifyEmail = settingService.get(cn.xmcraft.dreamport.server.settings.SettingService.KEY_ADMIN_NOTIFY_EMAIL,
                String.class);
        if (passed && notifyEmail != null && !notifyEmail.isBlank()) {
            mailService.sendAdminNotification(
                    "玩家 " + username + " 通过问卷：" + total + "/" + maxTotal + "\n" + overall,
                    notifyEmail.replace("\"", ""));
        }
        if (user.email() != null && !user.email().isBlank()) {
            mailService.sendQuestionnaireResult(username, user.email(), "zh",
                    passed ? "passed" : "failed", String.valueOf(total), String.valueOf(maxTotal),
                    passed ? "通过" : "未通过", results.toString(), overall);
        }
    }

    private String safeJson(Map<String, Object> answers) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(answers);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 从旧版 questionnaire.yml 格式导入（覆盖现有题库） */
    @SuppressWarnings("unchecked")
    public int importYaml(String yamlContent) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(yamlContent);
        List<Map<String, Object>> questions = (List<Map<String, Object>>) root.get("questions");
        if (questions == null) {
            throw new IllegalArgumentException("缺少 questions 节点");
        }
        QuestionnaireRecords.Questionnaire questionnaire = activeQuestionnaire();
        for (QuestionnaireRecords.Question old : questions(questionnaire.id())) {
            optionRepository.findByQuestionIdOrderBySortOrderAsc(old.id())
                    .forEach(optionRepository::delete);
            questionRepository.delete(old);
        }
        int sort = 0;
        for (Map<String, Object> qm : questions) {
            Map<String, Object> input = (Map<String, Object>) qm.get("input");
            QuestionnaireRecords.Question q = new QuestionnaireRecords.Question(
                    null, questionnaire.id(),
                    String.valueOf(qm.getOrDefault("question_zh", "")),
                    (String) qm.get("question_en"),
                    String.valueOf(qm.getOrDefault("type", "text")),
                    Boolean.TRUE.equals(qm.get("required")),
                    intOf(qm.get("max_score"), 0),
                    (String) qm.get("scoring_rule"),
                    input != null && Boolean.TRUE.equals(input.get("multiline")),
                    input == null ? null : intOf(input.get("min_length"), null),
                    input == null ? null : intOf(input.get("max_length"), null),
                    input == null ? null : intOf(input.get("min_selections"), null),
                    input == null ? null : intOf(input.get("max_selections"), null),
                    input == null ? null : (String) input.get("placeholder_zh"),
                    input == null ? null : (String) input.get("placeholder_en"),
                    sort++);
            QuestionnaireRecords.Question savedQ = questionRepository.save(q);
            int os = 0;
            for (Map<String, Object> om : (List<Map<String, Object>>) qm.getOrDefault("options", List.of())) {
                optionRepository.save(new QuestionnaireRecords.QuestionOption(
                        null, savedQ.id(), String.valueOf(om.getOrDefault("text_zh", "")),
                        (String) om.get("text_en"), intOf(om.get("score"), 0), os++));
            }
        }
        return questions.size();
    }

    private Integer intOf(Object o, Integer def) {
        if (o == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
