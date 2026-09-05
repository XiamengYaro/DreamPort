package cn.xmcraft.dreamport.server.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bearer JWT 解析过滤器：解析成功后把身份写入请求属性。
 * 不在此处拒绝请求（公开端点天然放行），由控制器用 {@link AuthUtil} 做授权判定。
 */
@Component
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
        if (header != null && header.startsWith("Bearer ")) {
            Claims claims = tokenService.parse(header.substring(7).trim());
            if (claims != null) {
                request.setAttribute(AuthUtil.ATTR_USERNAME, claims.getSubject());
                request.setAttribute(AuthUtil.ATTR_ROLE,
                        String.valueOf(claims.get("role", String.class)));
            }
        }
        chain.doFilter(request, response);
    }
}
