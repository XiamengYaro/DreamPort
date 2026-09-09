package cn.xmcraft.dreamport.server.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 邮件服务：{var} 占位符模板 + 双语 + 外置覆盖（plugins 目录风格：./email/ 优先，Rules.md §8）。
 * SMTP 未配置时进入日志模式（打印而非发送），dev 环境零依赖。
 * 模板继承自旧版（自研资产），存 classpath:email/{type}_{lang}.html。
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MailSenderHolder sender;
    private final MailProps props;
    private final cn.xmcraft.dreamport.server.settings.SystemSettingsService systemSettings;

    public MailService(MailProps props, MailSenderHolder sender,
                       cn.xmcraft.dreamport.server.settings.SystemSettingsService systemSettings) {
        this.props = props;
        this.sender = sender;
        this.systemSettings = systemSettings;
    }

    public boolean configured() {
        return props.host() != null && !props.host().isBlank();
    }

    private String lang(String lang) {
        return "en".equalsIgnoreCase(lang) ? "en" : "zh";
    }

    private String render(String type, String lang, String... kv) {
        String html = loadTemplate(type, lang(lang));
        // 公共品牌变量：logo_url / server_name / site_url（所有模板通用）
        for (int i = 0; i + 1 < kv.length; i += 2) {
            html = html.replace("{" + kv[i] + "}", kv[i + 1] == null ? "" : kv[i + 1]);
        }
        String siteUrl = stripTrailingSlash(brandSiteUrl());
        String logoPath = brandLogoPath();
        String logoUrl = "";
        if (logoPath != null && !logoPath.isBlank()) {
            logoUrl = logoPath.startsWith("http") ? logoPath : siteUrl + logoPath;
        }
        String logoCell = logoUrl.isBlank() ? ""
                : "<td style=\"padding-right: 12px; vertical-align: middle;\"><img src=\"" + logoUrl
                        + "\" alt=\"logo\" width=\"40\" height=\"40\" style=\"display: block; border: 0; border-radius: 8px;\" /></td>";
        html = html.replace("{logo_cell}", logoCell)
                   .replace("{logo_url}", logoUrl)
                   .replace("{server_name}", brandServerName())
                   .replace("{site_url}", siteUrl);
        return html;
    }

    private String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String brandServerName() {
        try {
            Object name = systemSettings.portalConfig().getOrDefault("server_name", "夏日小镇★XMCraft");
            return String.valueOf(name);
        } catch (Exception e) {
            return "夏日小镇★XMCraft";
        }
    }

    private String brandLogoPath() {
        try {
            Object logo = systemSettings.portalConfig().get("logo");
            return logo == null ? "" : String.valueOf(logo);
        } catch (Exception e) {
            return "";
        }
    }

    private String brandSiteUrl() {
        try {
            Object url = systemSettings.gameConfig().getOrDefault("webRegisterUrl", "");
            return String.valueOf(url);
        } catch (Exception e) {
            return "";
        }
    }

    private String loadTemplate(String type, String lang) {
        try {
            Path external = Path.of("email", type + "_" + lang + ".html");
            if (Files.exists(external)) {
                String externalHtml = Files.readString(external, StandardCharsets.UTF_8);
                // 旧版模板（无 {logo_cell} 品牌页眉占位符）不再采用，避免外置旧文件覆盖新设计
                if (externalHtml.contains("{logo_cell}")) {
                    return externalHtml;
                }
                log.info("外置模板 {}_{} 为旧版（缺少品牌页眉占位符），使用内置新版模板", type, lang);
            }
            return new String(new ClassPathResource("email/" + type + "_" + lang + ".html")
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("邮件模板加载失败 {}_{}: {}", type, lang, e.getMessage());
            return "<p>{content}</p>";
        }
    }

    private void send(String to, String subject, String html) {
        if (!configured()) {
            log.info("[Mail:日志模式] to={} subject={}（SMTP 未配置，跳过发送）", to, subject);
            return;
        }
        try {
            sender.send(to, subject, html, props);
            log.info("[Mail] 已发送 to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("[Mail] 发送失败 to={} subject={}: {}", to, subject, e.getMessage());
        }
    }

    private String subject(String key, String fallback) {
        return configured() && props.subjectOverride() != null && !props.subjectOverride().isBlank()
                ? props.subjectOverride() : fallback;
    }

    public void sendVerifyCode(String to, String code, String lang) {
        String l = lang(lang);
        String subject = "zh".equals(l) ? brandServerName() + "验证码" : "DreamPort verification code";
        send(to, subject, render("verify_code", l, "code", code));
    }

    public void sendReviewApproved(String username, String to, String lang) {
        String l = lang(lang);
        send(to, "zh".equals(l) ? "白名单申请已通过" : "Whitelist application approved",
                render("review_approved", l, "username", username));
    }

    public void sendReviewRejected(String username, String reason, String to, String lang) {
        String l = lang(lang);
        send(to, "zh".equals(l) ? "白名单申请被拒绝" : "Whitelist application rejected",
                render("review_rejected", l, "username", username, "reason", reason));
    }

    public void sendPasswordReset(String username, String to, String resetUrl, String lang) {
        String l = lang(lang);
        send(to, "zh".equals(l) ? brandServerName() + "密码重置" : "DreamPort password reset",
                render("password_reset", l, "username", username, "reset_url", resetUrl));
    }

    public void sendQuestionnaireResult(String username, String to, String lang,
                                        String passedClass, String totalScore, String maxScore,
                                        String passedText, String resultItems, String overallSummary) {
        String l = lang(lang);
        send(to, "zh".equals(l) ? "问卷结果通知" : "Questionnaire result",
                render("questionnaire_result", l,
                        "username", username, "passed_class", passedClass,
                        "totalScore", totalScore, "maxScore", maxScore,
                        "passed_text", passedText, "result_items", resultItems,
                        "overall_summary", overallSummary));
    }

    public void sendAccountBanned(String username, String to, String reason, String duration, String lang) {
        String l = lang(lang);
        String subject = "zh".equals(l) ? "账号封禁通知" : "Account banned notice";
        send(to, subject, render("account_banned", l,
                "username", username, "reason", reason, "duration", duration));
    }

    public void sendAccountUnbanned(String username, String to, String lang) {
        String l = lang(lang);
        String subject = "zh".equals(l) ? "封禁已解除" : "Ban lifted";
        send(to, subject, render("account_unbanned", l, "username", username));
    }

    public void sendAdminNotification(String content, String to) {
        if (to == null || to.isBlank()) {
            return;
        }
        send(to, "夏日小镇 · 新问卷通过提醒", content);
    }

    /** 反馈工单管理员回复通知(铃铛之外的邮件通道) */
    public void sendFeedbackReply(String username, String to, String feedbackTitle, String replyPreview, String lang) {
        if (to == null || to.isBlank()) {
            return;
        }
        String l = lang(lang);
        send(to, "zh".equals(l) ? "你的反馈有了新回复" : "New reply to your feedback",
                render("feedback_reply", l,
                        "username", username, "feedback_title", feedbackTitle, "reply_preview", replyPreview));
    }

    /** SMTP 配置（wl.mail.*） */
    public record MailProps(String host, int port, String username, String password,
                            String from, boolean ssl, String subjectOverride) {
    }

    /** 延迟构建的发送器容器（避免未配置时初始化 JavaMail 报错） */
    @org.springframework.stereotype.Component
    public static class MailSenderHolder {
        private final org.springframework.mail.javamail.JavaMailSenderImpl impl =
                new org.springframework.mail.javamail.JavaMailSenderImpl();

        public void send(String to, String subject, String html, MailProps props) {
            synchronized (impl) {
                impl.setHost(props.host());
                impl.setPort(props.port());
                impl.setUsername(props.username());
                impl.setPassword(props.password());
                impl.setDefaultEncoding(StandardCharsets.UTF_8.name());
                var mailProps = impl.getJavaMailProperties();
                mailProps.put("mail.smtp.auth", "true");
                mailProps.put("mail.smtp.connectiontimeout", "8000");
                mailProps.put("mail.smtp.timeout", "10000");
                if (props.ssl()) {
                    mailProps.put("mail.smtp.ssl.enable", "true");
                }
                try {
                    var message = impl.createMimeMessage();
                    var helper = new org.springframework.mail.javamail.MimeMessageHelper(message, false, "UTF-8");
                    helper.setFrom(props.from());
                    helper.setTo(to);
                    helper.setSubject(subject);
                    helper.setText(html, true);
                    impl.send(message);
                } catch (jakarta.mail.MessagingException e) {
                    throw new IllegalStateException("邮件构建失败", e);
                }
            }
        }
    }
}
