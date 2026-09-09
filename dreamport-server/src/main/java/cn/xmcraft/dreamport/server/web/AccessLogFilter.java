package cn.xmcraft.dreamport.server.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 网站访问日志（终端实时输出）：[访问] POST /api/login 200 23ms 1.2.3.4
 * 覆盖 /api/** 与 /internal/**；静态资源不记录。
 */
@Component
@Order(2)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("access");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            String uri = request.getRequestURI();
            if (uri.startsWith("/api/") || uri.startsWith("/internal/")) {
                String ip = ClientIp.realIp(request);
                String who = String.valueOf(request.getAttribute("wl.username"));
                log.info("[访问] {} {} {} {}ms {}{}",
                        request.getMethod(), uri, response.getStatus(),
                        System.currentTimeMillis() - start, ip,
                        "null".equals(who) ? "" : " @" + who);
            }
        }
    }
}
