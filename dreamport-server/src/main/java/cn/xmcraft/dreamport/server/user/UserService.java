package cn.xmcraft.dreamport.server.user;

import cn.xmcraft.dreamport.common.LoginCheckResponse;
import cn.xmcraft.dreamport.server.security.PasswordService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final cn.xmcraft.dreamport.server.config.WlProps props;

    public UserService(UserRepository userRepository, PasswordService passwordService,
                       cn.xmcraft.dreamport.server.settings.SettingService settingService,
                       cn.xmcraft.dreamport.server.config.WlProps props) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.settingService = settingService;
        this.props = props;
    }

    public enum RegisterError {INVALID_USERNAME, INVALID_EMAIL, INVALID_PASSWORD, USERNAME_TAKEN,
        EMAIL_TAKEN, EMAIL_DOMAIN_DENIED, EMAIL_LIMIT}

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
        // 邮箱域名白名单（Rules.md §3）
        var whitelist = props.register() != null ? props.register().emailDomainWhitelist() : null;
        boolean whitelistOn = props.register() != null && props.register().domainWhitelistEnabled()
                && whitelist != null && !whitelist.isEmpty();
        if (whitelistOn) {
            String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
            if (whitelist.stream().noneMatch(d -> String.valueOf(d).equalsIgnoreCase(domain))) {
                return new RegisterResult(null, RegisterError.EMAIL_DOMAIN_DENIED);
            }
        }
        // 单邮箱注册账号数上限
        int maxPerEmail = props.register() == null ? 2 : props.register().maxAccountsPerEmail();
        long sameEmail = userRepository.listAll().stream()
                .filter(u -> u.email() != null && u.email().equalsIgnoreCase(email)).count();
        if (sameEmail >= maxPerEmail) {
            return new RegisterResult(null, RegisterError.EMAIL_LIMIT);
        }
        boolean autoApprove = props.register() != null && props.register().autoApprove();
        UserRecord user = new UserRecord(null, username, email, autoApprove ? "approved" : "pending",
                "bcrypt", passwordService.hash(password), null,
                null, null, null, null, null, null, null, null, null,
                null, username, null, null, null, null, null, null, null, null,
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
            case "pending" -> props.questionnaire().enabled() && user.questionnaireScore() == 0
                    ? LoginCheckResponse.deny("login.needs_questionnaire")
                    : LoginCheckResponse.deny("login.pending");
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

    /** /internal/v1/admin-ops/list：待审核用户（游戏内 /xmw list 用） */
    public List<Map<String, Object>> pendingUsers() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserRecord u : userRepository.listAll()) {
            if ("pending".equals(u.status()) || "pending_review".equals(u.status())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("username", u.username());
                m.put("email", u.email());
                m.put("status", u.status());
                list.add(m);
            }
        }
        return list;
    }

    /** /internal/v1/admin-ops/info：单个用户信息 */
    public Map<String, Object> userInfo(String username) {
        var found = userRepository.findByUsernameIgnoreCase(username);
        if (found.isEmpty()) {
            return Map.of("found", false);
        }
        UserRecord u = found.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("found", true);
        m.put("username", u.username());
        m.put("email", u.email());
        m.put("status", u.status());
        m.put("regTime", u.regTime());
        m.put("banReason", u.banReason());
        m.put("banTime", u.banTime());
        return m;
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
