package cn.xmcraft.dreamport.server.audit;

import cn.xmcraft.dreamport.server.webhook.WebhookDispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 审计日志服务（修复旧版操作不落审计的点，Rules.md §9）；同时作为 Webhook 事件源骨架 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository auditRepository;
    private final WebhookDispatcherService webhookDispatcher;

    public AuditService(AuditRepository auditRepository, WebhookDispatcherService webhookDispatcher) {
        this.auditRepository = auditRepository;
        this.webhookDispatcher = webhookDispatcher;
    }

    public void log(String action, String operator, String target, String detail) {
        auditRepository.save(AuditRecord.of(action, operator, target, detail));
        // 终端实时输出内容修改/管理操作
        log.info("[修改] {} {} {} {}",
                operator, action, target == null ? "" : target,
                detail == null || detail.isBlank() ? "" : "｜ " + detail);
        dispatchWebhook(action, operator, target, detail);
    }

    public List<AuditRecord> recent() {
        return auditRepository.findTop200ByOrderByCreatedAtDesc();
    }

    /** 审计动作前缀 → Webhook 事件类型(空 detail 也分发,消费方自行过滤) */
    private void dispatchWebhook(String action, String operator, String target, String detail) {
        try {
            String event = switch (action) {
                case "approve" -> "review.approved";
                case "reject" -> "review.rejected";
                case "ban" -> "review.banned";
                case "unban", "unban_expired" -> "review.unbanned";
                case "delete" -> "review.deleted";
                case "appeal_approved" -> "appeal.approved";
                case "appeal_rejected" -> "appeal.rejected";
                case "questionnaire_submit" -> "questionnaire.submitted";
                case "reward_send" -> "reward.sent";
                case "feedback_create" -> "feedback.created";
                case "feedback_reply" -> "feedback.replied";
                case "feedback_close" -> "feedback.closed";
                case "poll_create" -> "poll.created";
                case "poll_open" -> "poll.opened";
                case "poll_close" -> "poll.closed";
                default -> null;
            };
            if (event == null && action != null) {
                if (action.startsWith("forum_")) {
                    event = "forum." + action.substring("forum_".length());
                } else if (action.startsWith("village_") || action.startsWith("machine_")) {
                    event = "community." + action;
                } else if (action.startsWith("server_")) {
                    event = "server." + action.substring("server_".length());
                }
            }
            if (event == null) {
                return;
            }
            webhookDispatcher.dispatch(event, Map.of(
                    "action", action == null ? "" : action,
                    "operator", operator == null ? "" : operator,
                    "target", target == null ? "" : target,
                    "detail", detail == null ? "" : detail));
        } catch (Exception e) {
            log.warn("Webhook 事件映射失败({}): {}", action, e.getMessage());
        }
    }
}
