package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.infra.MailService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 管理后台「邮件群发」:向所有注册并绑定邮箱的玩家发送邮件。
 * - 目标 = dp_user 中 email 非空合法的注册邮箱,小写去重(一个邮箱可能对应多个账号)
 * - 投递异步执行(MailService 内部已按 SMTP 配置决定真实发送或日志模式)
 */
@RestController
@RequestMapping("/api/admin/mail")
public class AdminMailController {

    private final UserRepository userRepository;
    private final MailService mailService;
    private final AuditService auditService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mail-broadcast");
        t.setDaemon(true);
        return t;
    });

    public AdminMailController(UserRepository userRepository, MailService mailService, AuditService auditService) {
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.auditService = auditService;
    }

    /** 群发目标:注册邮箱非空、格式合法,小写去重(一个邮箱可对应多个账号,只发一封) */
    static List<String> collectTargets(List<UserRecord> users) {
        return users.stream()
                .map(UserRecord::email)
                .filter(e -> e != null && !e.isBlank())
                .map(String::trim)
                .filter(e -> e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
    }

    @GetMapping("/targets")
    public ResponseEntity<Object> targets(HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        int count = collectTargets(userRepository.listAll()).size();
        return ResponseEntity.ok(ApiResponse.success(null,
                Map.of("count", count, "configured", mailService.configured())));
    }

    @PostMapping("/broadcast")
    public ResponseEntity<Object> broadcast(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (!admin(request)) {
            return forbidden();
        }
        String subject = Objects.toString(body.get("subject"), "").trim();
        String content = Objects.toString(body.get("content"), "").trim();
        if (subject.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("邮件主题不能为空"));
        }
        if (subject.length() > 200) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("邮件主题不能超过 200 字"));
        }
        if (content.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("邮件内容不能为空"));
        }
        if (content.length() > 20000) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("邮件内容不能超过 20000 字"));
        }
        List<String> targets = collectTargets(userRepository.listAll());
        if (targets.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("暂无可发送的玩家(没有已绑定邮箱的注册用户)"));
        }
        executor.submit(() -> targets.forEach(t -> mailService.sendBroadcast(t, subject, content)));
        auditService.log("mail_broadcast", me, null, "群发邮件「" + subject + "」至 " + targets.size() + " 名玩家");
        return ResponseEntity.ok(ApiResponse.success("已开始向 " + targets.size() + " 名玩家发送邮件",
                Map.of("total", targets.size())));
    }

    private static boolean admin(HttpServletRequest request) {
        return AuthUtil.currentUser(request) != null && AuthUtil.isAdmin(request);
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
    }
}
