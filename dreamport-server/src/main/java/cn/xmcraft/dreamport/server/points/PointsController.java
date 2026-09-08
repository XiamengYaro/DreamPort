package cn.xmcraft.dreamport.server.points;

import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 任务中心 / 积分 / 兑换(JWT 用户端)。
 */
@RestController
@RequestMapping("/api/points")
public class PointsController {

    private final TaskService taskService;
    private final MailService mailService;
    private final PointsService pointsService;

    public PointsController(TaskService taskService, MailService mailService, PointsService pointsService) {
        this.taskService = taskService;
        this.mailService = mailService;
        this.pointsService = pointsService;
    }

    private ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(401).body(ApiResponse.failure("请先登录"));
    }

    /** 任务中心聚合:余额/签到/任务进度 */
    @GetMapping("/center")
    public ResponseEntity<Object> center(jakarta.servlet.http.HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) return unauthorized();
        return ResponseEntity.ok(ApiResponse.success(null, taskService.center(me)));
    }

    /** 网页签到(每日一次) */
    @PostMapping("/signin")
    public ResponseEntity<Object> signin(jakarta.servlet.http.HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) return unauthorized();
        boolean first = taskService.signin(me, "web");
        if (!first) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("今日已签到"));
        }
        taskService.onSignin(me, "web");
        return ResponseEntity.ok(ApiResponse.success("签到成功"));
    }

    /** 领取已完成任务奖励 */
    @PostMapping("/claim")
    public ResponseEntity<Object> claim(@RequestBody Map<String, Object> body,
                                        jakarta.servlet.http.HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) return unauthorized();
        String taskId = String.valueOf(body.getOrDefault("taskId", ""));
        try {
            return ResponseEntity.ok(ApiResponse.success("领取成功", taskService.claim(me, taskId)));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    /** 兑换商店 */
    @GetMapping("/shop")
    public ResponseEntity<Object> shop(jakarta.servlet.http.HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) return unauthorized();
        return ResponseEntity.ok(ApiResponse.success(null, Map.of(
                "balance", pointsService.balance(me),
                "rewards", taskService.shopRewards())));
    }

    /** 积分兑换(扣分 + 游戏内邮件入队) */
    @PostMapping("/redeem")
    public ResponseEntity<Object> redeem(@RequestBody Map<String, Object> body,
                                         jakarta.servlet.http.HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) return unauthorized();
        String rewardId = String.valueOf(body.getOrDefault("rewardId", ""));
        try {
            return ResponseEntity.ok(ApiResponse.success("兑换成功", taskService.redeem(me, rewardId, mailService)));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }
}
