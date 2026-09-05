package cn.xmcraft.dreamport.server.websocket;

import cn.xmcraft.dreamport.server.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审核实时推送（ws://host:18899，契约对齐旧版 ReviewWebSocketServer）：
 * 连接后须发 {"type":"auth","token":...}（Bearer JWT）→ auth_success/auth_failed；
 * ping→pong；认证后广播 user_approved/user_rejected/user_banned/user_unbanned 等事件。
 */
@Service
public class ReviewPushService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ReviewPushService.class);
    private static final int MAX_CONNECTIONS = 50;

    private final TokenService tokenService;
    private final int wsPort;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile InnerServer server;
    private volatile boolean started;

    private final Set<WebSocket> authenticated = ConcurrentHashMap.newKeySet();

    public ReviewPushService(TokenService tokenService,
                             cn.xmcraft.dreamport.server.config.WlProps props) {
        this.tokenService = tokenService;
        this.wsPort = props.wsPort() == null ? 18899 : props.wsPort();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        server = new InnerServer(new InetSocketAddress(wsPort));
        server.setReuseAddr(true);
        server.start();
        started = true;
        log.info("WebSocket 推送服务已启动 :" + wsPort);
    }

    /** 广播事件给所有已认证连接 */
    public void pushEvent(String type, Map<String, ? extends Object> payload) {
        broadcastMap(type, new java.util.LinkedHashMap<String, Object>(payload));
    }

    public void broadcastMap(String type, Map<String, Object> payload) {
        if (!started) {
            return;
        }
        try {
            payload.put("type", type);
            payload.put("timestamp", System.currentTimeMillis());
            String json = mapper.writeValueAsString(payload);
            for (WebSocket conn : authenticated) {
                if (conn.isOpen()) {
                    conn.send(json);
                }
            }
        } catch (Exception e) {
            log.warn("WS 广播失败: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        if (server != null) {
            try {
                server.stop(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private class InnerServer extends WebSocketServer {

        private InnerServer(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            if (authenticated.size() >= MAX_CONNECTIONS && !authenticated.contains(conn)) {
                conn.close(1013, "连接数已达上限");
            }
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            authenticated.remove(conn);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            try {
                var node = mapper.readTree(message);
                String type = node.path("type").asText("");
                switch (type) {
                    case "auth" -> {
                        Claims claims = tokenService.parse(node.path("token").asText(""));
                        if (claims != null) {
                            authenticated.add(conn);
                            conn.send(mapper.writeValueAsString(Map.of("type", "auth_success", "username", claims.getSubject())));
                        } else {
                            conn.send(mapper.writeValueAsString(Map.of("type", "auth_failed")));
                            conn.close(4001, "认证失败");
                        }
                    }
                    case "ping" -> conn.send(mapper.writeValueAsString(Map.of("type", "pong")));
                    case "chat" -> {
                        if (!authenticated.contains(conn)) {
                            conn.send(mapper.writeValueAsString(Map.of("type", "error", "message", "未认证")));
                            return;
                        }
                        pushEvent("chat", Map.of(
                                "player", node.path("sender").asText("网页"),
                                "message", node.path("message").asText("")));
                    }
                    default -> conn.send(mapper.writeValueAsString(Map.of("type", "error", "message", "未认证")));
                }
            } catch (Exception e) {
                log.warn("WS 消息处理失败: {}", e.getMessage());
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            authenticated.remove(conn);
        }

        @Override
        public void onStart() {
            log.info("WS 服务器就绪: {}", getAddress());
        }
    }
}
