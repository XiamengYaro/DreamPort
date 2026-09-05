package cn.xmcraft.dreamport.server.verification;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.security.SecureRandom;

/** 密码重置令牌（dp_password_reset），1 小时有效（对齐旧版） */
@Table("dp_password_reset")
public record PasswordResetRecord(
        @Id Long id,
        String username,
        String token,
        Long expiresAt,
        Boolean used,
        Long createdAt
) {
    public static final long TTL_MS = 60 * 60_000L;

    public PasswordResetRecord {
        if (used == null) {
            used = false;
        }
        if (createdAt == null) {
            createdAt = System.currentTimeMillis();
        }
        if (expiresAt == null) {
            expiresAt = (createdAt) + TTL_MS;
        }
    }

    public static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public boolean valid(long now) {
        return !used && expiresAt > now;
    }

    public PasswordResetRecord markUsed() {
        return new PasswordResetRecord(id, username, token, expiresAt, true, createdAt);
    }
}
