package cn.xmcraft.dreamport.server.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SPA HTML 禁缓存：/ 与各前端路由（forward 到 index.html）响应 no-store，
 * 保证部署新版本后浏览器立即加载新前端；静态资源（/assets、/uploads）与 API 不受影响。
 */
@Component
@Order(1)
public class SpaCacheHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean asset = uri.startsWith("/assets/") || uri.startsWith("/uploads/")
                || uri.startsWith("/api/") || uri.startsWith("/internal/");
        if (!asset && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
