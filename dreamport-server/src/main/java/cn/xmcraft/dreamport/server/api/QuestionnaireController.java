package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.questionnaire.QuestionnaireRecords;
import cn.xmcraft.dreamport.server.questionnaire.QuestionOptionRepository;
import cn.xmcraft.dreamport.server.questionnaire.QuestionRepository;
import cn.xmcraft.dreamport.server.questionnaire.QuestionnaireRepository;
import cn.xmcraft.dreamport.server.questionnaire.QuestionnaireService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问卷端点：/api/questionnaire/{config,submit,stream} + 管理端题库管理。
 * FIX(legacy)：stream 与 submit 统一评分逻辑，stream 需登录（旧版副本无鉴权且行为不一致）。
 */
@RestController
@RequestMapping("/api")
public class QuestionnaireController {

    private final QuestionnaireService questionnaireService;
    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final cn.xmcraft.dreamport.server.user.UserRepository userRepository;
    private final cn.xmcraft.dreamport.server.audit.AuditService auditService;

    public QuestionnaireController(QuestionnaireService questionnaireService,
                                   QuestionnaireRepository questionnaireRepository,
                                   QuestionRepository questionRepository,
                                   QuestionOptionRepository optionRepository,
                                   cn.xmcraft.dreamport.server.user.UserRepository userRepository,
                                   cn.xmcraft.dreamport.server.audit.AuditService auditService) {
        this.questionnaireService = questionnaireService;
        this.questionnaireRepository = questionnaireRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public record SubmitBody(String username, Map<String, Object> answers, String language) {
    }

    public record SaveBody(String yaml) {
    }

    public record AddQuestionBody(QuestionnaireRecords.Question question,
                                  List<QuestionnaireRecords.QuestionOption> options) {
    }

    public record DeleteQuestionBody(Long id) {
    }

    /** 题库下发（双语按 language 取值） */
    @GetMapping("/questionnaire/config")
    public ResponseEntity<Object> config() {
        if (!questionnaireService.enabled()) {
            return ResponseEntity.ok(Map.of("enabled", false, "questions", List.of(), "passScore", 0));
        }
        var questionnaire = questionnaireService.activeQuestionnaire();
        List<Map<String, Object>> questions = new ArrayList<>();
        for (QuestionnaireRecords.Question q : questionnaireService.questions(questionnaire.id())) {
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("id", q.id());
            qm.put("question_zh", q.questionZh());
            qm.put("question_en", q.questionEn());
            qm.put("type", q.type());
            qm.put("required", q.required());
            qm.put("max_score", q.maxScore());
            qm.put("scoring_rule", q.scoringRule());
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("multiline", q.multiline());
            input.put("min_length", q.minLength());
            input.put("max_length", q.maxLength());
            input.put("min_selections", q.minSelections());
            input.put("max_selections", q.maxSelections());
            input.put("placeholder_zh", q.placeholderZh());
            input.put("placeholder_en", q.placeholderEn());
            qm.put("input", input);
            List<Map<String, Object>> options = new ArrayList<>();
            for (QuestionnaireRecords.QuestionOption o : questionnaireService.options(q.id())) {
                Map<String, Object> om = new LinkedHashMap<>();
                om.put("text_zh", o.textZh());
                om.put("text_en", o.textEn());
                om.put("score", o.score());
                options.add(om);
            }
            qm.put("options", options);
            questions.add(qm);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", true);
        body.put("questions", questions);
        body.put("passScore", questionnaire.passScore());
        return ResponseEntity.ok(body);
    }

    /** 同步提交（带 token+username 一致性校验，对齐旧版 /submit） */
    @PostMapping("/questionnaire/submit")
    public ResponseEntity<Object> submit(@RequestBody SubmitBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        if (body.username() != null && !body.username().equalsIgnoreCase(me)) {
            return ResponseEntity.status(403).body(ApiResponse.failure("不能替他人答题"));
        }
        var outcome = questionnaireService.submit(me, body.answers(), body.language());
        return ResponseEntity.ok(Map.of("totalScore", outcome.totalScore(), "maxScore", outcome.maxScore(),
                "passed", outcome.passed(), "results", outcome.results(), "summary", outcome.overallSummary()));
    }

    /** SSE 流式提交（逐题推送，最终写库/发邮件，与 submit 同一评分路径） */
    @PostMapping(value = "/questionnaire/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody SubmitBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        SseEmitter emitter = new SseEmitter(120_000L);
        if (me == null) {
            try {
                emitter.send(SseEmitter.event().data(Map.of("type", "error", "message", "未登录")));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }
        String username = body.username() != null && !body.username().isBlank() ? body.username() : me;
        var questionnaire = questionnaireService.activeQuestionnaire();
        var questions = questionnaireService.questions(questionnaire.id());
        new Thread(() -> {
            int total = 0;
            int maxTotal = 0;
            Map<String, Object> answers = body.answers();
            for (int i = 0; i < questions.size(); i++) {
                var q = questions.get(i);
                Object raw = answers.get(String.valueOf(q.id()));
                String answer = raw == null ? "" : String.valueOf(raw);
                var outcome = singleScore(q, answer, body.language());
                total += outcome.score();
                maxTotal += q.maxScore();
                try {
                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "question_scored", "index", i, "total", questions.size(),
                            "questionId", q.id(), "questionText",
                            "en".equals(body.language()) && q.questionEn() != null ? q.questionEn() : q.questionZh(),
                            "score", outcome.score(), "maxScore", q.maxScore(),
                            "reason", outcome.reason(), "totalScore", total)));
                    Thread.sleep(200);
                } catch (Exception e) {
                    emitter.completeWithError(e);
                    return;
                }
            }
            boolean passed = maxTotal > 0 && total * 100 / maxTotal >= questionnaire.passScore();
            try {
                var full = questionnaireService.submit(username, answers, body.language());
                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "complete", "totalScore", total, "maxScore", maxTotal,
                        "passed", passed, "summary", full.overallSummary())));
            } catch (Exception e) {
                log("stream 完成事件发送失败: {}", e.getMessage());
            }
            emitter.complete();
        }, "questionnaire-stream").start();
        return emitter;
    }

    private QuestionnaireService.QuestionResult singleScore(QuestionnaireRecords.Question q, String answer,
                                                            String lang) {
        // 复用 service 的评分路径：包一层单题 answers
        Map<String, Object> answers = Map.of(String.valueOf(q.id()), answer);
        var outcome = questionnaireService.previewScore(answers, lang);
        return outcome;
    }

    private void log(String s, String arg) {
        org.slf4j.LoggerFactory.getLogger(QuestionnaireController.class).warn(s, arg);
    }

    // ---------- 管理端题库管理 ----------

    @GetMapping("/admin/questionnaire/list")
    public ResponseEntity<Object> list(HttpServletRequest request) {
        var questionnaire = questionnaireService.activeQuestionnaire();
        List<Map<String, Object>> questions = new ArrayList<>();
        for (QuestionnaireRecords.Question q : questionnaireService.questions(questionnaire.id())) {
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("id", q.id());
            qm.put("question_zh", q.questionZh());
            qm.put("question_en", q.questionEn());
            qm.put("type", q.type());
            qm.put("required", q.required());
            qm.put("max_score", q.maxScore());
            qm.put("scoring_rule", q.scoringRule());
            qm.put("options", questionnaireService.options(q.id()));
            questions.add(qm);
        }
        return ResponseEntity.ok(Map.of("questionnaire", questionnaire, "questions", questions));
    }

    @PostMapping("/admin/questionnaire/save")
    public ResponseEntity<Object> save(@RequestBody String body, HttpServletRequest request) {
        if (!AuthUtil.isAdmin(request)) {
            return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
        }
        try {
            // 前端 api.saveQuestionnaire 直接以原始 YAML 文本为请求体
            int count = questionnaireService.importYaml(body);
            return ResponseEntity.ok(ApiResponse.success("已保存 " + count + " 题"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("YAML 解析失败: " + e.getMessage()));
        }
    }

    /** 管理员改分（契约对齐旧版 /admin/questionnaire/update） */
    @PostMapping("/admin/questionnaire/update")
    public ResponseEntity<Object> updateScore(@RequestBody java.util.Map<String, Object> body,
                                              HttpServletRequest request) {
        if (!AuthUtil.isAdmin(request)) {
            return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
        }
        String username = String.valueOf(body.get("username"));
        var userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("用户不存在"));
        }
        var user = userOpt.get();
        Integer score = body.get("questionnaireScore") == null ? user.questionnaireScore()
                : Integer.parseInt(String.valueOf(body.get("questionnaireScore")));
        Boolean passed = body.get("questionnairePassed") == null ? user.questionnairePassed()
                : Boolean.parseBoolean(String.valueOf(body.get("questionnairePassed")));
        String status = body.get("status") == null ? user.status() : String.valueOf(body.get("status"));
        if (!"approved".equals(status) && Boolean.TRUE.equals(passed) && "pending".equals(user.status())) {
            status = "pending_review";
        }
        UserRecord updated = new UserRecord(user.id(), user.username(), user.email(), status,
                user.passwordAlgo(), user.passwordHash(), user.regTime(), user.discordId(),
                user.qqNumber(), user.qqBoundAt(), score, passed, user.questionnaireReviewSummary(),
                user.questionnaireScoredAt(),
                body.get("questionnaireReasons") == null ? user.questionnaireReasons()
                        : String.valueOf(body.get("questionnaireReasons")),
                user.questionnaireAnswers(), user.minecraftUuid(), user.minecraftName(),
                user.microsoftVerified(), user.verifiedAt(), user.verifyType(), user.invitedBy(),
                user.bedrockUuid(), user.bedrockName(), user.bedrockVerified(), user.bedrockVerifiedAt(),
                user.banReason(), user.banTime(), user.avatar());
        userRepository.save(updated);
        auditService.log("admin_update_questionnaire", AuthUtil.currentUser(request), username, null);
        return ResponseEntity.ok(ApiResponse.success("问卷成绩已更新"));
    }

    /** 问卷详情（GET /admin/questionnaire/{username}，契约对齐旧版） */
    @GetMapping("/admin/questionnaire/{username}")
    public ResponseEntity<Object> questionnaireOf(@PathVariable String username,
                                                  HttpServletRequest request) {
        if (!AuthUtil.isAdmin(request)) {
            return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
        }
        var userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("用户不存在"));
        }
        var u = userOpt.get();
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("username", u.username());
        data.put("questionnaireScore", u.questionnaireScore());
        data.put("questionnairePassed", u.questionnairePassed());
        data.put("questionnaireReasons", u.questionnaireReasons());
        data.put("questionnaireAnswers", u.questionnaireAnswers());
        data.put("questionnaireReviewSummary", u.questionnaireReviewSummary());
        data.put("questionnaireScoredAt", u.questionnaireScoredAt());
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/admin/questionnaire/reset")
    public ResponseEntity<Object> reset(HttpServletRequest request) {
        if (!AuthUtil.isAdmin(request)) {
            return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
        }
        try (var in = getClass().getResourceAsStream("/questionnaire-default.yml")) {
            if (in == null) {
                return ResponseEntity.badRequest().body(ApiResponse.failure("默认题库缺失"));
            }
            int count = questionnaireService.importYaml(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            return ResponseEntity.ok(ApiResponse.success("已重置 " + count + " 题"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("重置失败: " + e.getMessage()));
        }
    }

    @PostMapping("/admin/questionnaire/add-question")
    public ResponseEntity<Object> addQuestion(@RequestBody AddQuestionBody body, HttpServletRequest request) {
        if (!AuthUtil.isAdmin(request)) {
            return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
        }
        var questionnaire = questionnaireService.activeQuestionnaire();
        var q = body.question();
        QuestionnaireRecords.Question saved = questionRepository.save(new QuestionnaireRecords.Question(
                null, questionnaire.id(), q.questionZh(), q.questionEn(), q.type(), q.required(),
                q.maxScore(), q.scoringRule(), q.multiline(), q.minLength(), q.maxLength(),
                q.minSelections(), q.maxSelections(), q.placeholderZh(), q.placeholderEn(),
                questionRepository.findByQuestionnaireIdOrderBySortOrderAsc(questionnaire.id()).size()));
        if (body.options() != null) {
            int sort = 0;
            for (QuestionnaireRecords.QuestionOption o : body.options()) {
                optionRepository.save(new QuestionnaireRecords.QuestionOption(null, saved.id(),
                        o.textZh(), o.textEn(), o.score(), sort++));
            }
        }
        return ResponseEntity.ok(ApiResponse.success("题目已添加"));
    }

    @PostMapping("/admin/questionnaire/delete-question")
    public ResponseEntity<Object> deleteQuestion(@RequestBody DeleteQuestionBody body,
                                                 HttpServletRequest request) {
        if (!AuthUtil.isAdmin(request)) {
            return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
        }
        long id = body.id() == null ? -1 : body.id();
        optionRepository.findByQuestionIdOrderBySortOrderAsc(id).forEach(optionRepository::delete);
        questionRepository.findById(id).ifPresent(questionRepository::delete);
        return ResponseEntity.ok(ApiResponse.success("题目已删除"));
    }
}
