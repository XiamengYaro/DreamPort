package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.config.WlProps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统信息：健康检查与版本（契约对齐旧版 /api/version 的响应键）。
 */
@RestController
@RequestMapping("/api")
public class SystemController {

    private final WlProps props;

    public SystemController(WlProps props) {
        this.props = props;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("version", props.version());
        body.put("virtualThread", Thread.currentThread().isVirtual());
        return body;
    }

    @GetMapping("/version")
    public Map<String, Object> version() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currentVersion", props.version());
        body.put("latestVersion", null);
        body.put("updateAvailable", false);
        return body;
    }
}
