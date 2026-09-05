package cn.xmcraft.dreamport.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * wl.* 配置项（对应 application.yml）。
 */
@ConfigurationProperties(prefix = "wl")
public record WlProps(String version, Security security, Internal internal,
                      String cors, boolean seedDemo) {

    public record Security(String jwtSecret, int jwtTtlDays) {
    }

    public record Internal(String serverToken) {
    }
}
