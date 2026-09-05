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
        cn.xmcraft.dreamport.server.config.StartupReporter.START_NANOS = System.nanoTime();
        cn.xmcraft.dreamport.server.config.StartupReporter.printBanner();

        SpringApplication app = new SpringApplication(DreamPortServerApplication.class);
        // 静态注册失败监听器：早期失败（如数据库连不上）时 bean 尚未创建，@EventListener 不会触发
        app.addListeners((org.springframework.context.ApplicationListener<org.springframework.boot.context.event.ApplicationFailedEvent>) event -> {
            Throwable root = event.getException();
            while (root.getCause() != null) {
                root = root.getCause();
            }
            System.out.println();
            System.out.println("✗ 启动失败：" + root.getMessage());
            System.out.println("  常见原因：MySQL 未启动 / config.yml 数据库连接信息有误 / 端口被占用");
            System.out.println("  修复 config.yml 后重新运行即可；详细堆栈见上方 ERROR 日志");
        });
        app.run(args);
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
    public cn.xmcraft.dreamport.server.migration.LegacyMigrator legacyMigrator(
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new cn.xmcraft.dreamport.server.migration.LegacyMigrator(jdbc);
    }

    @Bean
    public cn.xmcraft.dreamport.server.infra.MailService.MailProps mailProps(
            cn.xmcraft.dreamport.server.config.WlProps props) {
        var m = props.mail();
        return new cn.xmcraft.dreamport.server.infra.MailService.MailProps(
                m.host(), m.port(), m.username(), m.password(), m.from(), m.ssl(), m.subject());
    }
}
