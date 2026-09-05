package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 免登录审核状态查询。
 * FIX(legacy)：旧版读 X-Username 请求头而前端用 query 参数（Status.vue 拿不到用户名），
 * 此处改为标准 query 参数 username。
 */
@RestController
@RequestMapping("/api")
public class ReviewStatusController {

    private final UserRepository userRepository;

    public ReviewStatusController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/review/status")
    public Map<String, Object> status(@RequestParam String username) {
        Map<String, Object> body = new LinkedHashMap<>();
        var userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            body.put("found", false);
            return body;
        }
        UserRecord user = userOpt.get();
        body.put("found", true);
        body.put("username", user.username());
        body.put("status", user.status());
        body.put("questionnaireScore", user.questionnaireScore());
        body.put("questionnairePassed", user.questionnairePassed());
        body.put("banReason", user.banReason());
        return body;
    }
}
