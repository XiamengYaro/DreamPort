package cn.xmcraft.dreamport.server.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bearer JWT 解析过滤器：解析成功后把身份写入请求属性。
 * 不在此处拒绝请求（公开端点天然放行），由控制器用 {@link AuthUtil} 做授权判定。
 * @Order(0) 保证先于访问日志过滤器执行，使日志能带出当前用户（审计发现：默认最低序导致 @who 恒空）。
 */
@Component
@Order(0)
public class AuthFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    public AuthFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = null;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7).trim();
        } else if (request.getParameter("token") != null) {
            // SSE/EventSource 无法携带 Header，兼容旧前端 ?token= 传参
            token = request.getParameter("token");
        }
        if (token != null) {
            Claims claims = tokenService.parse(token);
            if (claims != null) {
                request.setAttribute(AuthUtil.ATTR_USERNAME, claims.getSubject());
                Object role = claims.get("role");
                request.setAttribute(AuthUtil.ATTR_ROLE, role == null ? null : String.valueOf(role));
            }
        }
        chain.doFilter(request, response);
    }
}
