package cn.xmcraft.dreamport.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * wl.* 基础设施配置（application.yml / 工作目录 config.yml / 环境变量）。
 * 业务设置（注册/AI评分/邀请/问卷/游戏）自 v0.5.9 起存数据库 dp_setting，
 * 由管理面板编辑（SystemSettingsService），不再经过本类。
 */
@ConfigurationProperties(prefix = "wl")
public record WlProps(Integer wsPort, String version, Security security, Internal internal,
                      String cors, Mail mail, String webRegisterUrl) {

    public record Security(String jwtSecret, int jwtTtlDays) {
    }

    public record Internal(String serverToken) {
    }

    public record Mail(String host, int port, String username, String password,
                       String from, boolean ssl, String subject) {
    }
}
