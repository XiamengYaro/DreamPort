package cn.xmcraft.dreamport.server.security;

import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP 两步验证(RFC 6238,原创实现,零外部依赖):
 * - HMAC-SHA1、6 位数字、30 秒步长、±1 窗口容错
 * - 密钥 Base32(RFC 4648 字母表 A-Z2-7)存储
 * - otpauth:// URI 供验证器 App 扫码
 */
@Service
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final long STEP_SECONDS = 30;
    private static final int WINDOW = 1;
    private static final int SECRET_BYTES = 20;

    private final SecureRandom random = new SecureRandom();

    /** 生成新密钥(Base32,20 字节熵) */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** 校验 6 位验证码,±1 步长窗口容错(应对时钟偏差) */
    public boolean verify(String base32Secret, String code) {
        if (base32Secret == null || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        byte[] key = base32Decode(base32Secret);
        long nowStep = Instant.now().getEpochSecond() / STEP_SECONDS;
        for (int i = -WINDOW; i <= WINDOW; i++) {
            if (constantTimeEquals(totpAt(key, nowStep + i), code)) {
                return true;
            }
        }
        return false;
    }

    /** 当前步长的验证码(仅测试/调试用) */
    public String currentCode(String base32Secret) {
        return totpAt(base32Decode(base32Secret), Instant.now().getEpochSecond() / STEP_SECONDS);
    }

    /** otpauth URI(验证器 App 扫码用) */
    public String otpauthUri(String secret, String account, String issuer) {
        return "otpauth://totp/" + urlEncode(issuer) + ":" + urlEncode(account)
                + "?secret=" + secret + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private String totpAt(byte[] key, long step) {
        byte[] counter = new byte[8];
        ByteBuffer.wrap(counter).putLong(step);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counter);
            // 动态截断(RFC 4226 §5.3)
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA1 不可用", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                b.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    static String base32Encode(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0, bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString();
    }

    static byte[] base32Decode(String input) {
        String clean = input.trim().replace("=", "").replace(" ", "").toUpperCase();
        int outLen = clean.length() * 5 / 8;
        byte[] out = new byte[outLen];
        int buffer = 0, bits = 0, index = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("非法 Base32 字符: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8 && index < outLen) {
                out[index++] = (byte) ((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value,
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
