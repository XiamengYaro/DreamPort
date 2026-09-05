package cn.xmcraft.dreamport.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * wl.* 配置项（对应 application.yml）。
 */
@ConfigurationProperties(prefix = "wl")
public record WlProps(Integer wsPort, String version, Security security, Internal internal,
                      String cors, boolean seedDemo, Mail mail,
                      Questionnaire questionnaire, Llm llm, Invite invite,
                      String adminNotifyEmail, String webRegisterUrl) {

    public record Security(String jwtSecret, int jwtTtlDays) {
    }

    public record Internal(String serverToken) {
    }

    public record Mail(String host, int port, String username, String password,
                       String from, boolean ssl, String subject) {
    }

    public record Questionnaire(boolean enabled, int passScore) {
    }

    public record Llm(boolean enabled, String apiBase, String apiKey, String model,
                      int timeoutMs, int maxConcurrency, String systemPrompt) {
    }

    public record Invite(boolean enabled, int codeExpiryDays, int maxInvitesPerUser) {
    }
}
