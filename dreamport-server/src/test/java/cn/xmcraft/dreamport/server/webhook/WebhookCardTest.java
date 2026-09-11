package cn.xmcraft.dreamport.server.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞书卡片正文中文化:事件名、字段标签、时间格式化,元字段(event/time/action)不再直铺。
 */
class WebhookCardTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 工单回复卡片正文中文化() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "event", "feedback.replied",
                "action", "feedback_reply",
                "operator", "Xia_Meng_",
                "target", "#1",
                "detail", "回复工单: 123",
                "time", 1789147578124L));
        String content = WebhookDispatcherService.buildCardContent(mapper.readTree(body));
        assertTrue(content.contains("工单回复"), "事件名应为中文: " + content);
        assertTrue(content.contains("操作人") && content.contains("Xia_Meng_"));
        assertTrue(content.contains("对象") && content.contains("#1"));
        assertTrue(content.contains("详情") && content.contains("回复工单: 123"));
        assertTrue(content.contains("时间") && content.contains("2026-09-12 01:26:18"),
                "时间应格式化为中文可读: " + content);
        // 元字段不再以原始键直铺
        assertFalse(content.contains("**event**"), "不应直铺 event 键");
        assertFalse(content.contains("**action**"), "不应直铺 action 键");
        assertFalse(content.contains("**time**"), "不应直铺 time 键");
        assertFalse(content.contains("1789147578124"), "不应出现毫秒时间戳");
    }

    @Test
    void 未知事件保留原事件名且字段仍中文() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "event", "forum.post_created",
                "operator", "Xia_Meng_",
                "target", "帖子 #12"));
        String content = WebhookDispatcherService.buildCardContent(mapper.readTree(body));
        assertTrue(content.contains("forum.post_created"), "未收录事件保留原事件名");
        assertTrue(content.contains("操作人"), "operator 字段仍映射中文标签");
        assertTrue(content.contains("帖子 #12"));
    }
}
