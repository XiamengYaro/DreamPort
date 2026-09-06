package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.qq.BindCodeService;
import cn.xmcraft.dreamport.server.qq.QqBindingService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.user.UserRepository;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户侧 QQ 绑定端点（JWT 鉴权，AuthFilter 写入请求属性）：
 * 查询绑定状态、凭验证码绑定、解绑。流程见 docs/ASTRBOT_PLAN.md §5.2。
 */
@RestController
@RequestMapping("/api/user/qq")
public class UserQqController {

    private final UserRepository userRepository;
    private final BindCodeService bindCodeService;
    private final QqBindingService bindingService;
    private final AuditService auditService;

    public UserQqController(UserRepository userRepository, BindCodeService bindCodeService,
                            QqBindingService bindingService, AuditService auditService) {
        this.userRepository = userRepository;
        this.bindCodeService = bindCodeService;
        this.bindingService = bindingService;
        this.auditService = auditService;
    }

    public record BindBody(String code) {
    }

    private ResponseEntity<Object> deny() {
        return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
    }

    @GetMapping("/status")
    public ResponseEntity<Object> status(HttpServletRequest request) {
        var userOpt = currentUser(request);
        if (userOpt.isEmpty()) {
            return deny();
        }
        var u = userOpt.get();
        boolean bound = u.qqNumber() != null && !u.qqNumber().isBlank();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bound", bound);
        data.put("qq", bound ? QqBindingService.mask(u.qqNumber()) : "");
        data.put("boundAt", bound ? u.qqBoundAt() : null);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @PostMapping("/bind")
    public ResponseEntity<Object> bind(@RequestBody BindBody body, HttpServletRequest request) {
        var userOpt = currentUser(request);
        if (userOpt.isEmpty()) {
            return deny();
        }
        String username = userOpt.get().username();
        if (body == null || body.code() == null || body.code().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("请输入验证码"));
        }
        var result = bindCodeService.consume(body.code().trim(), "user:" + username.toLowerCase());
        if (result.status() == BindCodeService.Status.LOCKED) {
            return ResponseEntity.status(429).body(ApiResponse.failure("错误次数过多，请 10 分钟后再试"));
        }
        if (result.status() == BindCodeService.Status.BAD_CODE) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("验证码无效或已过期"));
        }
        bindingService.bind(userOpt.get(), result.qq());
        auditService.log("qq_bind", username, "", "qq=" + QqBindingService.mask(result.qq()));
        return ResponseEntity.ok(ApiResponse.success("绑定成功",
                Map.of("qq", QqBindingService.mask(result.qq()))));
    }

    @PostMapping("/unbind")
    public ResponseEntity<Object> unbind(HttpServletRequest request) {
        var userOpt = currentUser(request);
        if (userOpt.isEmpty()) {
            return deny();
        }
        var u = userOpt.get();
        if (u.qqNumber() == null || u.qqNumber().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("尚未绑定 QQ"));
        }
        bindingService.unbindQq(u.qqNumber());
        auditService.log("qq_unbind", u.username(), "", "");
        return ResponseEntity.ok(ApiResponse.success("已解绑"));
    }

    private java.util.Optional<cn.xmcraft.dreamport.server.user.UserRecord> currentUser(HttpServletRequest request) {
        String username = AuthUtil.currentUser(request);
        if (username == null) {
            return java.util.Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(username);
    }
}
