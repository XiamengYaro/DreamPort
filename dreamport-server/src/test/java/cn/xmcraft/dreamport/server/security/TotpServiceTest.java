package cn.xmcraft.dreamport.server.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TOTP 单测(纯内存):Base32 编解码、RFC 6238 生成/验证、窗口容错。
 */
class TotpServiceTest {

    private final TotpService totp = new TotpService();

    @Test
    void base32RoundTrip() {
        byte[] bytes = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, (byte) 0xFF, 0x7F, 0x10, 0x20};
        assertEquals(new String(bytes), new String(TotpService.base32Decode(TotpService.base32Encode(bytes))));
        // Base32 字母表不含 0/1/8/9
        String secret = totp.generateSecret();
        assertTrue(secret.matches("[A-Z2-7]+"), "secret 应仅含 A-Z2-7: " + secret);
    }

    @Test
    void generatedSecretHasSufficientEntropy() {
        assertEquals(32, totp.generateSecret().length(), "20 字节 → 32 个 Base32 字符");
        assertNotEquals(totp.generateSecret(), totp.generateSecret());
    }

    @Test
    void currentCodeIsSixDigitsAndVerifies() {
        String secret = totp.generateSecret();
        String code = totp.currentCode(secret);
        assertTrue(code.matches("\\d{6}"));
        assertTrue(totp.verify(secret, code), "当前步长的验证码应验证通过");
    }

    @Test
    void verifyRejectsWrongOrMalformedCode() {
        String secret = totp.generateSecret();
        assertFalse(totp.verify(secret, "000000".equals(totp.currentCode(secret)) ? "111111" : "000000"));
        assertFalse(totp.verify(secret, "abcdef"), "非数字拒绝");
        assertFalse(totp.verify(secret, "12345"), "位数不足拒绝");
        assertFalse(totp.verify(secret, null));
        assertFalse(totp.verify(null, "123456"));
    }

    @Test
    void verifyToleratesAdjacentWindow() {
        // RFC 6238 ±1 窗口:用"上一个步长"的验证码也应通过(等价时钟偏差 <30s)
        String secret = totp.generateSecret();
        long prevStep = Instant.now().getEpochSecond() / 30 - 1;
        String prevCode = codeAt(secret, prevStep);
        assertTrue(totp.verify(secret, prevCode), "±1 窗口内的码应通过");
    }

    private String codeAt(String secret, long step) {
        try {
            var method = TotpService.class.getDeclaredMethod("totpAt", byte[].class, long.class);
            method.setAccessible(true);
            return (String) method.invoke(totp, TotpService.base32Decode(secret), step);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
