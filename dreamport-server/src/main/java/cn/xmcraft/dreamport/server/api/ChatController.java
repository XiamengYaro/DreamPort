package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.chat.ChatService;
import cn.xmcraft.dreamport.server.qq.QqBridgeService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 网页聊天室（契约对齐旧版 /api/chat/*）：SSE 订阅 + 发送（同步广播进游戏由 P5 插件轮询/推送实现）。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final QqBridgeService qqBridge;

    public ChatController(ChatService chatService, QqBridgeService qqBridge) {
        this.chatService = chatService;
        this.qqBridge = qqBridge;
    }

    public record SendBody(String message) {
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest request) {
        if (AuthUtil.currentUser(request) == null) {
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalStateException("未登录"));
            return emitter;
        }
        return chatService.subscribe();
    }

    @PostMapping("/send")
    public ResponseEntity<Object> send(@RequestBody SendBody body, HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        if (body.message() == null || body.message().isBlank() || body.message().length() > 256) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("消息为空或过长"));
        }
        chatService.broadcast(me, body.message());
        qqBridge.onWebChat(me, body.message());
        return ResponseEntity.ok(ApiResponse.success("已发送"));
    }

    @GetMapping("/history")
    public ResponseEntity<Object> history() {
        return ResponseEntity.ok(Map.of("history", chatService.history()));
    }

    @PostMapping("/save")
    public ResponseEntity<Object> save(@RequestBody Map<String, String> body) {
        chatService.appendHistory(body.getOrDefault("player", "网页"), body.getOrDefault("message", ""));
        return ResponseEntity.ok(ApiResponse.success("已保存"));
    }
}
