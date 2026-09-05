package cn.xmcraft.dreamport.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DreamPort 独立后端入口。
 * 端口 18898（REST + SPA），18899（WebSocket）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DreamPortServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DreamPortServerApplication.class, args);
    }

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Bean
    public cn.xmcraft.dreamport.server.infra.MailService.MailProps mailProps(
            cn.xmcraft.dreamport.server.config.WlProps props) {
        var m = props.mail();
        return new cn.xmcraft.dreamport.server.infra.MailService.MailProps(
                m.host(), m.port(), m.username(), m.password(), m.from(), m.ssl(), m.subject());
    }
}
