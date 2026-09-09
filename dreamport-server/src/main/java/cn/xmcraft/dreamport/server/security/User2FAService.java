package cn.xmcraft.dreamport.server.security;

import cn.xmcraft.dreamport.server.infra.MailService;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.web.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户 2FA 状态管理:绑定/启用/停用、恢复码、邮箱备用码、登录验证与防爆破锁定。
 * - 邮箱备用码:内存态 5 分钟,与注册验证码池隔离
 * - 登录挑战(challenge):密码验证通过后的临时凭据,5 分钟,内存态;仅能用于 2FA 验证,不授予任何会话权限
 * - 防爆破:连错 5 次锁 15 分钟
 */
@Service
public class User2FAService {

    private static final Logger log = LoggerFactory.getLogger(User2FAService.class);
    private static final long CHALLENGE_TTL_MS = 5 * 60_000L;
    private static final int MAX_FAILS = 5;
    private static final long LOCK_MS = 15 * 60_000L;
    private static final int RECOVERY_CODES = 8;

    public record TwoFaRow(String username, String secret, boolean enabled, String recoveryHashes) {
    }

    /** 登录中间态:2FA 已启用(需验证码)或管理员强制绑定(needs_setup) */
    public record Challenge(String username, boolean admin, boolean forcedSetup, long expiresAt) {
    }

    private final JdbcTemplate jdbc;
    private final TotpService totpService;
    private final PasswordService passwordService;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final RateLimiter rateLimiter;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, FailState> fails = new ConcurrentHashMap<>();
    /** 邮箱备用码:email → {code, expiresAt} */
    private final Map<String, EmailCode> emailCodes = new ConcurrentHashMap<>();

    private record EmailCode(String code, long expiresAt) {
    }

    private record FailState(int count, long lockedUntil) {
    }

    public User2FAService(JdbcTemplate jdbc, TotpService totpService, PasswordService passwordService,
                          UserRepository userRepository, MailService mailService, RateLimiter rateLimiter) {
        this.jdbc = jdbc;
        this.totpService = totpService;
        this.passwordService = passwordService;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.rateLimiter = rateLimiter;
    }

    // ---------- 状态与绑定 ----------

    /** 已启用 2FA? */
    public boolean isEnabled(String username) {
        TwoFaRow row = findRow(username);
        return row != null && row.enabled();
    }

    /** 绑定中(存在行但未启用)? */
    public boolean isPending(String username) {
        TwoFaRow row = findRow(username);
        return row != null && !row.enabled();
    }

    /** 开始绑定:生成新密钥(覆盖旧绑定),@return Base32 密钥 */
    public String startEnroll(String username) {
        String secret = totpService.generateSecret();
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO dp_user_2fa (username, secret, enabled, created_at) VALUES (?, ?, 0, ?) "
                + "ON DUPLICATE KEY UPDATE secret = VALUES(secret), enabled = 0, recovery_hashes = NULL", username, secret, now);
        return secret;
    }

    /** 验证码确认,启用 2FA;@return 一次性明文恢复码 */
    public List<String> enable(String username, String code) {
        TwoFaRow row = findRow(username);
        if (row == null || !totpService.verify(row.secret(), code)) {
            return List.of();
        }
        List<String> recovery = generateRecoveryCodes();
        List<String> hashes = recovery.stream().map(c -> passwordService.hash(c)).toList();
        try {
            jdbc.update("UPDATE dp_user_2fa SET enabled = 1, verified_at = ?, recovery_hashes = ? WHERE username = ?",
                    System.currentTimeMillis(), mapper.writeValueAsString(hashes), username);
        } catch (Exception e) {
            throw new IllegalStateException("恢复码序列化失败", e);
        }
        log.info("[2FA] {} 已启用两步验证", username);
        return recovery;
    }

    /** 停用:需密码 + 验证码(TOTP 或恢复码) */
    public boolean disable(String username, String password, String code) {
        var user = userRepository.findByUsernameIgnoreCase(username);
        if (user.isEmpty() || passwordService.verify(password, user.get().passwordAlgo(),
                user.get().passwordHash()) == PasswordService.VerifyResult.FAIL) {
            return false;
        }
        TwoFaRow row = findRow(username);
        if (row == null) {
            return false;
        }
        if (!totpService.verify(row.secret(), code) && !consumeRecovery(row, code)) {
            return false;
        }
        jdbc.update("DELETE FROM dp_user_2fa WHERE username = ?", username);
        log.info("[2FA] {} 已停用两步验证", username);
        return true;
    }

    // ---------- 登录验证 ----------

    /**
     * 登录验证码校验:TOTP → 恢复码 → 邮箱备用码。
     * @return true 校验成功;false 失败(内部计失败次数,超限抛 IllegalStateException 锁定)
     */
    public boolean verifyLogin(String username, String code) {
        FailState state = fails.get(username.toLowerCase());
        if (state != null && state.lockedUntil() > System.currentTimeMillis()) {
            throw new IllegalStateException("失败次数过多,请 15 分钟后再试");
        }
        boolean ok = doVerify(username, code);
        if (ok) {
            fails.remove(username.toLowerCase());
        } else {
            int count = (state == null ? 0 : state.count()) + 1;
            fails.put(username.toLowerCase(), new FailState(count,
                    count >= MAX_FAILS ? System.currentTimeMillis() + LOCK_MS : 0));
        }
        return ok;
    }

    private boolean doVerify(String username, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String trimmed = code.trim().replace("-", "").replace(" ", "");
        TwoFaRow row = findRow(username);
        if (row != null && row.enabled()) {
            if (totpService.verify(row.secret(), trimmed)) {
                return true;
            }
            if (consumeRecovery(row, code)) {
                return true;
            }
        }
        return consumeEmailCode(username, trimmed);
    }

    /** 发送邮箱备用验证码(3 次/5 分钟) */
    public boolean sendEmailCode(String username) {
        if (!rateLimiter.allow("2fa-email:" + username.toLowerCase(), 3, 5 * 60_000L)) {
            return false;
        }
        var user = userRepository.findByUsernameIgnoreCase(username);
        if (user.isEmpty() || user.get().email() == null || user.get().email().isBlank()) {
            return false;
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        emailCodes.put(username.toLowerCase(), new EmailCode(code, System.currentTimeMillis() + 5 * 60_000L));
        mailService.sendVerifyCode(user.get().email(), code, "zh");
        return true;
    }

    private boolean consumeEmailCode(String username, String code) {
        String key = username.toLowerCase();
        EmailCode entry = emailCodes.get(key);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) {
            emailCodes.remove(key);
            return false;
        }
        if (entry.code().equals(code)) {
            emailCodes.remove(key);
            return true;
        }
        return false;
    }

    // ---------- 登录挑战 ----------

    public String createChallenge(String username, boolean admin, boolean forcedSetup) {
        String id = randomId();
        challenges.put(id, new Challenge(username, admin, forcedSetup, System.currentTimeMillis() + CHALLENGE_TTL_MS));
        return id;
    }

    /** @return 挑战对应的用户名;无效/过期返回 null */
    public Challenge consumeChallenge(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return null;
        }
        Challenge c = challenges.get(challengeId);
        if (c == null || System.currentTimeMillis() > c.expiresAt()) {
            challenges.remove(challengeId);
            return null;
        }
        return c;
    }

    public void removeChallenge(String challengeId) {
        challenges.remove(challengeId);
    }

    // ---------- 内部 ----------

    private TwoFaRow findRow(String username) {
        var rows = jdbc.queryForList(
                "SELECT username, secret, enabled, recovery_hashes FROM dp_user_2fa WHERE username = ?", username);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> r = rows.get(0);
        return new TwoFaRow(String.valueOf(r.get("username")), String.valueOf(r.get("secret")),
                ((Number) r.get("enabled")).intValue() != 0,
                r.get("recovery_hashes") == null ? null : String.valueOf(r.get("recovery_hashes")));
    }

    /** 校验恢复码并标记已用(单次) */
    private boolean consumeRecovery(TwoFaRow row, String code) {
        if (row.recoveryHashes() == null || code == null || code.isBlank()) {
            return false;
        }
        try {
            List<String> hashes = mapper.readValue(row.recoveryHashes(),
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            String trimmed = code.trim();
            // 兼容带连字符(XXXX-XXXX)与去掉连字符两种输入形态
            String normalized = trimmed.replace("-", "").replace(" ", "");
            for (int i = 0; i < hashes.size(); i++) {
                boolean rawMatch = passwordService.verify(trimmed, "bcrypt", hashes.get(i)) == PasswordService.VerifyResult.OK;
                boolean normalizedMatch = !normalized.equals(trimmed)
                        && passwordService.verify(normalized, "bcrypt", hashes.get(i)) == PasswordService.VerifyResult.OK;
                if (rawMatch || normalizedMatch) {
                    hashes.remove(i);
                    jdbc.update("UPDATE dp_user_2fa SET recovery_hashes = ? WHERE username = ?",
                            mapper.writeValueAsString(hashes), row.username());
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("恢复码校验异常: {}", e.getMessage());
        }
        return false;
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODES);
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) {
                if (j == 4) {
                    sb.append('-');
                }
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    private String randomId() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
