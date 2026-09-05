package cn.xmcraft.dreamport.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 前端 SPA 托管（替代旧版 StaticFileHandler）：
 * - classpath:/static 为前端构建产物
 * - 已知路由回退到 index.html（vue-router history 模式）
 * - /uploads/** 服务运行时上传的图片（头像/机器截图/门户图片）
 */
@Configuration
public class WebStaticConfig implements WebMvcConfigurer {

    private static final List<String> SPA_ROUTES = List.of(
            "/", "/docs", "/whitelist", "/login", "/register", "/forgot-password", "/reset-password",
            "/verify", "/questionnaire", "/questionnaire-result", "/dashboard", "/leaderboard",
            "/village", "/players", "/machines", "/map", "/admin",
            "/docs/*", "/player/*", "/questionnaire/*");

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:static/uploads/");
    }

    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        for (String route : SPA_ROUTES) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
