package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.migration.LegacyMigrator;
import cn.xmcraft.dreamport.server.migration.SqlDumpStagingImporter;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.settings.SettingService;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理后台数据迁移（管理端上传旧库 dump → 暂存导入 → 复制到 dp_* 表）。
 * 安全：管理员 JWT；幂等：dp_user 非空时拒绝；隔离：dump 数据只进暂存表，完成后清理。
 */
@RestController
@RequestMapping("/api/admin/migration")
public class MigrationController {

    private final SqlDumpStagingImporter stagingImporter;
    private final LegacyMigrator migrator;
    private final SettingService settingService;
    private final AuditService auditService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public MigrationController(SqlDumpStagingImporter stagingImporter, LegacyMigrator migrator,
                               SettingService settingService, AuditService auditService,
                               org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.stagingImporter = stagingImporter;
        this.migrator = migrator;
        this.settingService = settingService;
        this.auditService = auditService;
        this.jdbc = jdbc;
    }

    /** dp_user 是否只含播种的演示账号 */
    private boolean onlySeedUsers() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dp_user", Integer.class);
        Integer seeds = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dp_user WHERE username IN ('demo', 'demo2')", Integer.class);
        return count != null && count.equals(seeds);
    }

    @PostMapping("/upload")
    public ResponseEntity<Object> upload(@RequestParam("file") MultipartFile file,
                                         HttpServletRequest request) {
        if (!AuthUtil.isAdmin(request)) {
            return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("文件为空"));
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".sql")) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("请上传 .sql 格式的旧库导出文件（mysqldump）"));
        }
        if (migrator.dpUserHasData()) {
            // 播种的演示账号（demo/demo2）不视为真实数据：自动清理后放行
            if (!onlySeedUsers()) {
                return ResponseEntity.badRequest().body(ApiResponse.failure(
                        "当前数据库已存在用户数据，禁止覆盖导入。如需重新迁移请先清空 dp_* 表。"));
            }
            jdbc.update("DELETE FROM dp_user WHERE username IN ('demo', 'demo2')");
            auditService.log("migration_seed_cleanup", AuthUtil.currentUser(request), "demo/demo2", null);
        }
        String sql;
        try {
            sql = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("文件读取失败"));
        }
        // 1. dump → 暂存表
        SqlDumpStagingImporter.StagingOutcome staging = stagingImporter.importDump(sql);
        if (staging.staged().isEmpty()) {
            stagingImporter.dropStaging();
            return ResponseEntity.badRequest().body(ApiResponse.failure(
                    "未在文件中识别到旧版数据表（xmwhitelist_users 等 9 张），请确认导出文件包含白名单数据"));
        }
        // 2. 暂存表 → dp_* 表
        Map<String, String> mapping = new LinkedHashMap<>();
        for (SqlDumpStagingImporter.StagedTable t : staging.staged()) {
            String target = switch (t.originalName()) {
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
            if (target != null) {
                mapping.put(t.stagingName(), target);
            }
        }
        Map<String, Object> report = migrator.migrateFromStaging(mapping);
        // 3. 清理暂存
        stagingImporter.dropStaging();
        report.put("stagedTables", staging.staged());
        report.put("fileName", file.getOriginalFilename());
        auditService.log("migration_upload", AuthUtil.currentUser(request),
                file.getOriginalFilename(), String.valueOf(report.get("importedRows")));
        return ResponseEntity.ok(ApiResponse.success("迁移完成", report));
    }

    @GetMapping("/report")
    public ResponseEntity<Object> report(HttpServletRequest request) {
        if (!AuthUtil.isAdmin(request)) {
            return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
        }
        return ResponseEntity.ok(settingService.getMap("migration.report"));
    }
}
