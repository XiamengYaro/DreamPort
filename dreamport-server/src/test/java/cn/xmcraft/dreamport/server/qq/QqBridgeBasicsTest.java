package cn.xmcraft.dreamport.server.qq;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3 互通基础件单测:SeqQueue 序号队列 + QqBridgeService 静态工具(渲染/群绑定解析)。
 */
class QqBridgeBasicsTest {

    @Test
    void seqQueueAssignsMonotonicSeq() {
        SeqQueue<String> q = new SeqQueue<>(10);
        assertEquals(1, q.offer("a").seq());
        assertEquals(2, q.offer("b").seq());
        assertEquals(2, q.latest());
        assertEquals("a", q.since(0).get(0).payload());
        assertEquals("b", q.since(1).get(0).payload());
        assertTrue(q.since(2).isEmpty());
    }

    @Test
    void seqQueueEvictsOldestBeyondCapacity() {
        SeqQueue<String> q = new SeqQueue<>(3);
        for (int i = 1; i <= 5; i++) {
            q.offer("m" + i);
        }
        assertEquals(5, q.latest());
        var items = q.since(0);
        assertEquals(3, items.size());
        assertEquals("m3", items.get(0).payload());
        assertEquals("m5", items.get(2).payload());
        // 已淘汰条目不可再拉取
        assertEquals(3, q.since(1).size());
    }

    @Test
    void seqQueueRejectsInvalidCapacity() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SeqQueue<>(0));
    }

    @Test
    void renderReplacesPlaceholdersLiterally() {
        String out = QqBridgeService.render("[{server}] {player}: {message}",
                Map.of("server", "生存", "player", "Steve", "message", "你好 {player}"));
        // 消息文本中的 "{player}" 属于内容,单遍渲染不再二次替换
        assertEquals("[生存] Steve: 你好 {player}", out);
    }

    @Test
    void renderTreatsNullValueAsEmpty() {
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("sender", null);
        vars.put("message", "hi");
        assertEquals("[QQ] : hi", QqBridgeService.render("[QQ] {sender}: {message}", vars));
    }

    @Test
    void parseBindingsAcceptsNumbersAndDefaults() {
        var raw = List.of(
                Map.<Object, Object>of("group", 123456, "mode", "prefix", "prefix", "#"),
                Map.<Object, Object>of("group", 789L),
                "garbage",
                Map.<Object, Object>of("group", "not-a-number"));
        var bindings = QqBridgeService.parseBindings(raw);
        assertEquals(2, bindings.size());
        assertEquals(123456, bindings.get(0).group());
        assertEquals("prefix", bindings.get(0).mode());
        assertEquals("#", bindings.get(0).prefix());
        assertTrue(bindings.get(0).forwardJoinQuit());
        assertEquals(789, bindings.get(1).group());
        assertEquals("all", bindings.get(1).mode());
    }

    @Test
    void parseBindingsEmptyOrNull() {
        assertTrue(QqBridgeService.parseBindings(null).isEmpty());
        assertTrue(QqBridgeService.parseBindings(List.of()).isEmpty());
    }
}
