package cn.xmcraft.dreamport.server.security;

import cn.xmcraft.dreamport.server.config.WlProps;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发/校验（无状态，重启不掉线——修复旧版内存 token 重启失效问题，Rules.md §9-5）。
 * role 取值：user / admin。
 */
@Service
public class TokenService {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";

    private final SecretKey key;
    private final long ttlMs;

    public TokenService(WlProps props) {
        this.key = Keys.hmacShaKeyFor(props.security().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.ttlMs = props.security().jwtTtlDays() * 86_400_000L;
    }

    public String issue(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMs)))
                .signWith(key)
                .compact();
    }

    /** 无效/过期返回 null */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
