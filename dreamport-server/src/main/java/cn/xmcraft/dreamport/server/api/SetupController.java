package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.migration.LegacyMigrator;
import cn.xmcraft.dreamport.server.migration.SqlDumpStagingImporter;
import cn.xmcraft.dreamport.server.security.PasswordService;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 首次启动向导（检测到无用户时开放，完成后永久关闭）：
 * ① 注册管理员账号（approved + 写入管理员名单）
 * ② 选择部署模式：全新部署 / 上传旧库 dump 导入（导入后若命中同名账号则「认领」——重置密码并设为管理员）
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_-]{3,16}$");

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final SettingService settingService;
    private final SqlDumpStagingImporter stagingImporter;
    private final LegacyMigrator migrator;
    private final AuditService auditService;

    public SetupController(UserRepository userRepository, PasswordService passwordService,
                           SettingService settingService, SqlDumpStagingImporter stagingImporter,
                           LegacyMigrator migrator, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.settingService = settingService;
        this.stagingImporter = stagingImporter;
        this.migrator = migrator;
        this.auditService = auditService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("setupRequired", setupRequired());
    }

    public record SetupReport(boolean adminCreated, boolean adminClaimedExisting,
                              Map<String, Object> importReport) {
    }

    @PostMapping
    public ResponseEntity<Object> setup(@RequestParam String username,
                                        @RequestParam String password,
                                        @RequestParam(required = false) String email,
                                        @RequestParam(defaultValue = "fresh") String mode,
                                        @RequestParam(required = false) MultipartFile file) {
        if (!setupRequired()) {
            return ResponseEntity.status(403).body(ApiResponse.failure("系统已完成初始化，请直接登录"));
        }
        if (username == null || !USERNAME.matcher(username).matches()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("管理员用户名不合法（3-16 位字母数字_-）"));
        }
        if (password == null || password.length() < 8) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("密码至少 8 位"));
        }
        Map<String, Object> importReport = null;
        boolean claimedExisting = false;

        // ① 导入模式：先导 dump（此时 dp_user 为空，满足幂等守卫），再处理管理员账号
        if ("import".equals(mode)) {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.failure("导入模式需要上传旧库 .sql 文件"));
            }
            String lower = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            if (!lower.endsWith(".sql")) {
                return ResponseEntity.badRequest().body(ApiResponse.failure("请上传 .sql 格式的旧库导出文件"));
            }
            String sql;
            try {
                sql = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(ApiResponse.failure("文件读取失败"));
            }
            var staging = stagingImporter.importDump(sql);
            if (staging.staged().isEmpty()) {
                stagingImporter.dropStaging();
                return ResponseEntity.badRequest().body(ApiResponse.failure(
                        "未在文件中识别到旧版数据表（xmwhitelist_users 等 9 张），请确认导出文件包含白名单数据"));
            }
            Map<String, String> mapping = new LinkedHashMap<>();
            for (SqlDumpStagingImporter.StagedTable t : staging.staged()) {
                String target = targetOf(t.originalName());
                if (target != null) {
                    mapping.put(t.stagingName(), target);
                }
            }
            importReport = migrator.migrateFromStaging(mapping);
            stagingImporter.dropStaging();
            importReport.put("stagedTables", staging.staged());

            // 同名账号「认领」：旧数据里已有该用户名 → 重置密码/邮箱并设为 approved
            var existing = userRepository.findByUsernameIgnoreCase(username);
            if (existing.isPresent()) {
                UserRecord user = existing.get();
                userRepository.save(withAdminCredential(user, password, email));
                claimedExisting = true;
            }
        }

        // ② 管理员账号
        boolean adminCreated = false;
        if (!claimedExisting) {
            if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.failure("该用户名已存在"));
            }
            userRepository.save(new UserRecord(null, username, email, "approved",
                    "bcrypt", passwordService.hash(password), null,
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null));
            adminCreated = true;
        }
        // ③ 写入管理员名单
        List<Object> admins = new ArrayList<>(settingService.get(SettingService.KEY_ADMINS, List.class) == null
                ? List.of() : settingService.get(SettingService.KEY_ADMINS, List.class));
        if (admins.stream().noneMatch(a -> String.valueOf(a).equalsIgnoreCase(username))) {
            admins.add(username);
            settingService.set(SettingService.KEY_ADMINS, admins);
        }
        auditService.log("setup", username, "mode=" + mode, null);
        settingService.set("setup.completed", true);

        return ResponseEntity.ok(ApiResponse.success("初始化完成，请登录",
                new SetupReport(adminCreated, claimedExisting, importReport)));
    }

    private boolean setupRequired() {
        return userRepository.count() == 0;
    }

    private String targetOf(String originalName) {
        return switch (originalName) {
            case "xmwhitelist_users" -> "dp_user";
            case "xmwhitelist_audits" -> "dp_audit_log";
            case "xmwhitelist_invites" -> "dp_invite";
            case "xmwhitelist_notifications" -> "dp_notification";
            case "xmwhitelist_pending_logins" -> "dp_pending_login";
            case "xmwhitelist_password_resets" -> "dp_password_reset";
            case "xmwhitelist_appeals" -> "dp_appeal";
            case "village_trades" -> "dp_village_trade";
            case "public_machines" -> "dp_public_machine";
            default -> null;
        };
    }

    private UserRecord withAdminCredential(UserRecord user, String password, String email) {
        return new UserRecord(user.id(), user.username(),
                email != null && !email.isBlank() ? email : user.email(),
                "approved", "bcrypt", passwordService.hash(password), user.regTime(),
                user.discordId(), user.qqNumber(), user.qqBoundAt(), user.questionnaireScore(),
                user.questionnairePassed(), user.questionnaireReviewSummary(), user.questionnaireScoredAt(),
                user.questionnaireReasons(), user.questionnaireAnswers(), user.minecraftUuid(),
                user.minecraftName(), user.microsoftVerified(), user.verifiedAt(), user.verifyType(),
                user.invitedBy(), user.bedrockUuid(), user.bedrockName(), user.bedrockVerified(),
                user.bedrockVerifiedAt(), user.banReason(), user.banTime(), user.banUntil(), user.avatar());
    }
}
