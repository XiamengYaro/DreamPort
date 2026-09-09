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
 * 上传目录旧 SVG 文件的存储型 XSS 兜底（审计 M3）：
 * 历史已存在的 /uploads/*.svg 无法删光，给这类响应附加 sandbox CSP，
 * 即使内容含脚本也在独立沙箱内执行，无法读写站点上下文。
 * 新上传已不再接受 .svg 扩展名。
 */
@Component
@Order(3)
public class UploadedSvgFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/uploads/") && uri.toLowerCase().endsWith(".svg")) {
            response.setHeader("Content-Security-Policy",
                    "default-src 'none'; style-src 'unsafe-inline'; sandbox");
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        chain.doFilter(request, response);
    }
}
