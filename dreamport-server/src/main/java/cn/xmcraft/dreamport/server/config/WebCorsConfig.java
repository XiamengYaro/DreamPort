package cn.xmcraft.dreamport.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 配置：dev 默认 "*"；生产必须显式配置 wl.cors.allowed-origins（Rules.md §3）。
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final WlProps props;

    public WebCorsConfig(WlProps props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        String origins = props.cors() == null ? "*" : props.cors();
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
        registry.addMapping("/internal/**")
                .allowedOriginPatterns(origins.split(","))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
