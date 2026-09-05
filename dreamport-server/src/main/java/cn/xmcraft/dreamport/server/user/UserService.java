package cn.xmcraft.dreamport.server.user;

import cn.xmcraft.dreamport.common.LoginCheckResponse;
import cn.xmcraft.dreamport.server.security.PasswordService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 账户域核心服务：注册、登录验证（含旧哈希透明升级）、进服校验决策。
 * P1 为骨架实现；问卷/邀请/Microsoft 验证等分支在 P3 完整接入。
 */
@Service
public class UserService {

    /** 与旧版 register.username_regex 默认值一致 */
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_-]{3,16}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final cn.xmcraft.dreamport.server.settings.SettingService settingService;

    public UserService(UserRepository userRepository, PasswordService passwordService,
                       cn.xmcraft.dreamport.server.settings.SettingService settingService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.settingService = settingService;
    }

    public enum RegisterError {INVALID_USERNAME, INVALID_EMAIL, INVALID_PASSWORD, USERNAME_TAKEN, EMAIL_TAKEN}

    public record RegisterResult(UserRecord user, RegisterError error) {
        public boolean ok() {
            return user != null;
        }
    }

    public RegisterResult register(String username, String email, String password) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            return new RegisterResult(null, RegisterError.INVALID_USERNAME);
        }
        if (email == null || !EMAIL.matcher(email).matches()) {
            return new RegisterResult(null, RegisterError.INVALID_EMAIL);
        }
        if (password == null || password.length() < 6) {
            return new RegisterResult(null, RegisterError.INVALID_PASSWORD);
        }
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            return new RegisterResult(null, RegisterError.USERNAME_TAKEN);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return new RegisterResult(null, RegisterError.EMAIL_TAKEN);
        }
        UserRecord user = new UserRecord(null, username, email, "pending",
                "bcrypt", passwordService.hash(password), null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null);
        return new RegisterResult(userRepository.save(user), null);
    }

    public Optional<UserRecord> authenticate(String username, String rawPassword) {
        Optional<UserRecord> found = userRepository.findByUsernameIgnoreCase(username);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UserRecord user = found.get();
        var result = passwordService.verify(rawPassword, user.passwordAlgo(), user.passwordHash());
        return switch (result) {
            case FAIL -> Optional.empty();
            case LEGACY_OK -> {
                // 旧哈希验证成功 → 透明升级 bcrypt（数据兼容策略，PROJECT_DOCUMENTATION §4.3）
                UserRecord upgraded = userRepository
                        .save(user.withPassword("bcrypt", passwordService.hash(rawPassword)));
                yield Optional.of(upgraded);
            }
            case OK -> Optional.of(user);
        };
    }

    /**
     * 进服校验决策：按用户状态映射 allow/deny + 与旧版一致的 i18n reasonKey。
     * 维护模式读 dp_setting（持久化——修复旧版重启失效）。
     */
    public LoginCheckResponse loginDecision(String username) {
        boolean maintenance = maintenance();
        Optional<UserRecord> found = userRepository.findByUsernameIgnoreCase(username);
        if (found.isEmpty()) {
            return maintenance ? denyMaintained() : LoginCheckResponse.deny("login.not_registered");
        }
        UserRecord user = found.get();
        if (maintenance) {
            return denyMaintained();
        }
        return switch (user.status()) {
            case "approved" -> LoginCheckResponse.allow();
            case "pending" -> LoginCheckResponse.deny("login.pending");
            case "pending_review" -> LoginCheckResponse.deny("login.pending_review");
            case "invited_pending" -> LoginCheckResponse.deny("login.invited_pending");
            case "pending_verify" -> LoginCheckResponse.deny("login.pending_verify");
            case "rejected" -> LoginCheckResponse.deny("login.rejected");
            case "banned" -> LoginCheckResponse.deny(
                    user.banReason() == null || user.banReason().isBlank()
                            ? "login.banned" : "login.banned_reason");
            default -> LoginCheckResponse.deny("login.unknown_status");
        };
    }

    private LoginCheckResponse denyMaintained() {
        return new LoginCheckResponse(cn.xmcraft.dreamport.common.Protocol.DECISION_DENY,
                "maintenance.kick", true);
    }

    private boolean maintenance() {
        try {
            return settingService.getBool(cn.xmcraft.dreamport.server.settings.SettingService.KEY_MAINTENANCE,
                    false);
        } catch (Exception e) {
            return false;
        }
    }
}
