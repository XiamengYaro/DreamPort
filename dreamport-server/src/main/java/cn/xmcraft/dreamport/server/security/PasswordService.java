package cn.xmcraft.dreamport.server.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码双算法服务（数据兼容核心，Rules.md §5）：
 * - 新写入一律 bcrypt(cost 10)
 * - 旧版格式 {@code $SHA$<base64盐>$<sha256_hex(盐+密码)>} 保留可验证（恒时比较），
 *   验证成功后由调用方透明升级为 bcrypt（见 UserService）
 * 旧格式参考：../XMWhitelist-Legacy/plugin/src/main/java/com/xmwhitelist/util/PasswordUtil.java
 */
@Service
public class PasswordService {

    /** 验证结果；LEGACY_OK 表示旧算法验证成功，调用方应立即升级哈希 */
    public enum VerifyResult {OK, LEGACY_OK, FAIL}

    private static final SecureRandom RANDOM = new SecureRandom();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

    /** 新密码哈希（bcrypt） */
    public String hash(String rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    /** 按旧版算法生成哈希（仅用于迁移样本/测试向量） */
    public String legacyHash(String rawPassword) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltB64 = Base64.getEncoder().encodeToString(salt);
        return "$SHA$" + saltB64 + "$" + sha256Hex(saltB64 + rawPassword);
    }

    public VerifyResult verify(String rawPassword, String algo, String stored) {
        if (rawPassword == null || stored == null) {
            return VerifyResult.FAIL;
        }
        return switch (algo == null || algo.isBlank() ? "bcrypt" : algo) {
            case "bcrypt" -> bcrypt.matches(rawPassword, stored) ? VerifyResult.OK : VerifyResult.FAIL;
            case "legacy_sha256" -> verifyLegacy(rawPassword, stored) ? VerifyResult.LEGACY_OK : VerifyResult.FAIL;
            default -> VerifyResult.FAIL;
        };
    }

    private boolean verifyLegacy(String rawPassword, String stored) {
        // 拆分：["", "SHA", base64盐, 64位hex]；Base64 字符集不含 '$'，拆分安全
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"SHA".equals(parts[1])) {
            return false;
        }
        String expected = sha256Hex(parts[2] + rawPassword);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                parts[3].toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                  .append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
