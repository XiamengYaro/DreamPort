package cn.xmcraft.dreamport.server.chat;

import cn.xmcraft.dreamport.server.settings.SettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 网页聊天室（SSE 推送 + dp_setting 持久化历史，上限 500 条——对齐旧版 ChatSseManager）。
 * 上限 50 连接（契约同旧版）。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_EMITTERS = 50;
    private static final int MAX_HISTORY = 500;

    private final SettingService settingService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public ChatService(SettingService settingService) {
        this.settingService = settingService;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        if (emitters.size() >= MAX_EMITTERS) {
            emitter.completeWithError(new IllegalStateException("连接数已达上限"));
            return emitter;
        }
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "connected")));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** 游戏聊天（插件上报）或网页聊天广播 + 历史落库 */
    public void broadcast(String player, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "chat");
        payload.put("player", player);
        payload.put("message", message);
        payload.put("timestamp", System.currentTimeMillis());
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(json));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
        appendHistory(player, message);
    }

    @SuppressWarnings("unchecked")
    public synchronized void appendHistory(String player, String message) {
        List<Map<String, Object>> history = settingService.get(SettingService.KEY_CHAT_HISTORY, List.class);
        if (history == null) {
            history = new ArrayList<>();
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("player", player);
        entry.put("message", message);
        entry.put("timestamp", System.currentTimeMillis());
        history = new ArrayList<>(history);
        history.add(entry);
        if (history.size() > MAX_HISTORY) {
            history = history.subList(history.size() - MAX_HISTORY, history.size());
        }
        settingService.set(SettingService.KEY_CHAT_HISTORY, history);
    }

    public List<Map<String, Object>> history() {
        List<Map<String, Object>> history = settingService.get(SettingService.KEY_CHAT_HISTORY, List.class);
        return history == null ? List.of() : history;
    }
}
