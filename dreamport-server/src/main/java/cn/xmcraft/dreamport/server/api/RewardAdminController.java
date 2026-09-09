package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.audit.AuditService;
import cn.xmcraft.dreamport.server.reward.RewardKitService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端奖励发放(礼包/指令包):
 * - 礼包模板 CRUD(创建后由服内管理员 /xmw kit save 采集背包内容)
 * - 发放:单个玩家或全员(approved),礼包快照或手填指令包
 */
@RestController
@RequestMapping("/api/admin/rewards")
public class RewardAdminController {

    private final RewardKitService rewardKitService;
    private final AuditService auditService;

    public RewardAdminController(RewardKitService rewardKitService, AuditService auditService) {
        this.rewardKitService = rewardKitService;
        this.auditService = auditService;
    }

    private boolean admin(HttpServletRequest request) {
        return AuthUtil.currentUser(request) != null && AuthUtil.isAdmin(request);
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
    }

    private String op(HttpServletRequest request) {
        return AuthUtil.currentUser(request) == null ? "system" : AuthUtil.currentUser(request);
    }

    public record CreateKitBody(String name, String note) {
    }

    public record UpdateKitBody(String note, String commandsText) {
    }

    public record SendBody(List<String> usernames, Boolean all, Long kitId,
                           String commandsText, String title, String note) {
    }

    @GetMapping("/kits")
    public ResponseEntity<Object> kits(HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        return ResponseEntity.ok(ApiResponse.success(null, Map.of("kits", rewardKitService.list())));
    }

    @PostMapping("/kits")
    public ResponseEntity<Object> createKit(@RequestBody CreateKitBody body, HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        var result = rewardKitService.create(body.name(), body.note(), op(request));
        return result.success()
                ? ResponseEntity.ok(ApiResponse.success(result.message()))
                : ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
    }

    @DeleteMapping("/kits/{id}")
    public ResponseEntity<Object> deleteKit(@PathVariable long id, HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        var result = rewardKitService.delete(id, op(request));
        return result.success()
                ? ResponseEntity.ok(ApiResponse.success(result.message()))
                : ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
    }

    /** 编辑礼包:备注 + 附加指令(每行一条;与采集物品混合发放) */
    @PutMapping("/kits/{id}")
    public ResponseEntity<Object> updateKit(@PathVariable long id, @RequestBody UpdateKitBody body,
                                            HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        var result = rewardKitService.update(id, body.note(), body.commandsText(), op(request));
        return result.success()
                ? ResponseEntity.ok(ApiResponse.success(result.message()))
                : ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
    }

    @PostMapping("/send")
    public ResponseEntity<Object> send(@RequestBody SendBody body, HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        if (body == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("请求体为空"));
        }
        var outcome = rewardKitService.send(body.usernames(), Boolean.TRUE.equals(body.all()),
                body.kitId(), body.commandsText(), body.title(), body.note(), op(request));
        if (outcome.sent() == 0 && outcome.skipped() == 0) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(outcome.message()));
        }
        return ResponseEntity.ok(ApiResponse.success(outcome.message(),
                Map.of("sent", outcome.sent(), "skipped", outcome.skipped())));
    }
}
