package cn.xmcraft.dreamport.server.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一响应包装（兼容旧前端的 success/message/msg 读法，Rules.md §3）。
 */
public final class ApiResponse {

    private ApiResponse() {
    }

    public static Map<String, Object> success(String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", message);
        if (data != null) {
            body.put("data", data);
        }
        return body;
    }

    public static Map<String, Object> success(String message) {
        return success(message, null);
    }

    public static Map<String, Object> failure(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("msg", message);
        return body;
    }

    public static Map<String, Object> failure(String message, Object data) {
        Map<String, Object> body = failure(message);
        body.put("data", data);
        return body;
    }
}
