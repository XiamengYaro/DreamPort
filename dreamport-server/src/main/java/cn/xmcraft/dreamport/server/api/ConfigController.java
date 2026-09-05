package cn.xmcraft.dreamport.server.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 站点公开配置（契约对齐旧版 /api/config；P4 从 dp_setting 读取，P1 返回骨架值）。
 */
@RestController
@RequestMapping("/api")
public class ConfigController {

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serverName", "夏日小镇");
        body.put("subtitle", "DreamPort · 梦港");
        body.put("announcement", "欢迎来到夏日小镇！");
        body.put("registerEnabled", true);
        body.put("authMethods", java.util.List.of("email"));
        return body;
    }
}
