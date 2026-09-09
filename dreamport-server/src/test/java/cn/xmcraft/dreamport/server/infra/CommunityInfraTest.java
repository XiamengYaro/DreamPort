package cn.xmcraft.dreamport.server.infra;

import cn.xmcraft.dreamport.server.settings.SettingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 共享 UGC 基建组件单测(纯内存桩,无 Spring 上下文/数据库)。
 */
class CommunityInfraTest {

    /** SettingService 桩:getRaw 返回预设词表(其余 SQL 一律抛错,用不到) */
    private static SensitiveWordFilter filterOf(String rawWords) {
        JdbcTemplate stub = new JdbcTemplate() {
            @Override
            public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
                @SuppressWarnings("unchecked")
                T value = (T) rawWords;
                return rawWords == null ? List.of() : List.of(value);
            }
        };
        return new SensitiveWordFilter(new SettingService(stub));
    }

    @Test
    void sensitiveFilterReplacesWithSameLengthStars() {
        var filter = filterOf("笨蛋,坏词");
        assertEquals("你是个**啊", filter.filter("你是个笨蛋啊"));
        assertEquals("他真是个**啊**", filter.filter("他真是个笨蛋啊坏词"));
    }

    @Test
    void sensitiveFilterPassesWhenNoWordsOrNoHit() {
        assertEquals("正常内容", filterOf("笨蛋").filter("正常内容"));
        assertEquals("正常内容", filterOf(null).filter("正常内容"));
        assertEquals("", filterOf("笨蛋").filter(""));
    }

    @Test
    void sensitiveFilterHitsDetection() {
        assertTrue(filterOf("敏感词").hits("包含敏感词的文本"));
        assertFalse(filterOf("敏感词").hits("干净文本"));
        assertFalse(filterOf(null).hits("任意"));
    }

    @Test
    void rateLimiterWindowBlocksOverLimit() {
        var limiter = new SimpleRateLimiter();
        assertTrue(limiter.allowWindow("u:alice", 1, 30_000));
        assertFalse(limiter.allowWindow("u:alice", 1, 30_000));
        assertTrue(limiter.allowWindow("u:bob", 1, 30_000), "不同 key 互不影响");
    }

    @Test
    void rateLimiterDailyQuota() {
        var limiter = new SimpleRateLimiter();
        assertTrue(limiter.allowDaily("fb:alice", 3));
        assertTrue(limiter.allowDaily("fb:alice", 3));
        assertTrue(limiter.allowDaily("fb:alice", 3));
        assertFalse(limiter.allowDaily("fb:alice", 3));
        assertTrue(limiter.allowDaily("fb:bob", 3), "不同用户互不影响");
    }
}
