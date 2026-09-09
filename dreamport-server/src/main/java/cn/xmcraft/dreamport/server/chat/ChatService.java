package cn.xmcraft.dreamport.server.chat;

import cn.xmcraft.dreamport.server.settings.SettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 网页聊天室：SSE 推送 + dp_chat_message 表持久化（docs/CHAT_SERVERINFO_PLAN.md §2.2）。
 * 消息四源 origin：game（插件上报）/ web（网页发送）/ qq（AstrBot 上行）/ system（进退服）。
 * 保留策略：7 天 + 5 万条硬顶（每小时清理，决策点已确认）；
 * 旧 dp_setting `chat.history` JSON 停写弃用（写放大与 500 条上限问题，v1.1 起）。
 * SSE 上限 50 连接（契约同旧版）。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_EMITTERS = 50;
    static final int RETENTION_DAYS = 7;
    static final int MAX_ROWS = 50_000;
    public static final int DEFAULT_PAGE_SIZE = 200;
    public static final String KEY_SENSITIVE_WORDS = "sensitive.words";

    private final JdbcTemplate jdbc;
    private final SettingService settingService;
    private final cn.xmcraft.dreamport.server.infra.SensitiveWordFilter sensitiveWordFilter;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public ChatService(SettingService settingService, JdbcTemplate jdbc,
                       cn.xmcraft.dreamport.server.infra.SensitiveWordFilter sensitiveWordFilter) {
        this.settingService = settingService;
        this.jdbc = jdbc;
        this.sensitiveWordFilter = sensitiveWordFilter;
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

    /** 敏感词过滤(委托共享过滤器,聊天与论坛/反馈等 UGC 统一口径) */
    public String filterSensitive(String text) {
        return sensitiveWordFilter.filter(text);
    }

    /** 游戏聊天（插件上报）或网页聊天广播 + 落库（旧签名，等价 web 源） */
    public void broadcast(String player, String message) {
        broadcast("web", player, message, null);
    }

    /** 统一广播：SSE 推送 + dp_chat_message 落库 */
    public void broadcast(String origin, String player, String message, String serverId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "chat");
        payload.put("player", player);
        payload.put("message", message);
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("origin", origin);
        payload.put("server_id", serverId);
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
        insert(origin, player, message, serverId);
    }

    /** 保留端点（/api/chat/save）：等价 web 源落库 */
    public synchronized void appendHistory(String player, String message) {
        insert("web", player, message, null);
    }

    void insert(String origin, String player, String message, String serverId) {
        try {
            jdbc.update("INSERT INTO dp_chat_message (origin, player, message, server_id, created_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    origin, player == null ? "?" : player,
                    message == null ? "" : message, serverId, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("聊天消息落库失败: {}", e.getMessage());
        }
    }

    /**
     * 历史查询（7 天窗口内，按时间正序）。
     * @param beforeId 游标：取 id &lt; beforeId 的前一页；null 表示从最新开始
     * @param origin   可选过滤；null/blank 不过滤
     * @return {history: [...], nextBefore: 最早一条 id 或 null}
     */
    public Map<String, Object> historyPage(Long beforeId, int limit, String origin) {
        int size = Math.max(1, Math.min(limit, 500));
        long windowStart = System.currentTimeMillis() - RETENTION_DAYS * 24L * 3600_000L;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, origin, player, message, server_id, created_at FROM dp_chat_message "
                        + "WHERE created_at >= ? AND (? IS NULL OR id < ?) AND (? IS NULL OR origin = ?) "
                        + "ORDER BY id DESC LIMIT ?",
                windowStart, beforeId, beforeId, origin, origin, size);
        List<Map<String, Object>> history = new ArrayList<>(rows.size());
        long nextBefore = Long.MAX_VALUE;
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ((Number) row.get("id")).longValue());
            item.put("origin", row.get("origin"));
            item.put("player", row.get("player"));
            item.put("message", row.get("message"));
            item.put("server_id", row.get("server_id"));
            item.put("timestamp", ((Number) row.get("created_at")).longValue());
            history.add(item);
            nextBefore = Math.min(nextBefore, ((Number) row.get("id")).longValue());
        }
        java.util.Collections.reverse(history);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("history", history);
        result.put("nextBefore", history.size() < size ? null : nextBefore);
        return result;
    }

    /** 保留清理（每小时）：7 天过期数据 + 超过硬顶的最旧数据 */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 120_000)
    public void retention() {
        try {
            long cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 3600_000L;
            int expired = jdbc.update("DELETE FROM dp_chat_message WHERE created_at < ?", cutoff);
            int overCap = jdbc.update("DELETE o FROM dp_chat_message o "
                    + "LEFT JOIN (SELECT id FROM dp_chat_message ORDER BY id DESC LIMIT " + MAX_ROWS + ") keep "
                    + "ON o.id = keep.id WHERE keep.id IS NULL");
            if (expired > 0 || overCap > 0) {
                log.info("[清理] 聊天历史: 过期 {} 条, 超限 {} 条", expired, overCap);
            }
        } catch (Exception e) {
            log.warn("聊天历史清理失败: {}", e.getMessage());
        }
    }
}
