package cn.xmcraft.dreamport.server.titles;

import cn.xmcraft.dreamport.server.settings.SettingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 称号/成就配置回退语义单测(修复后台"删光后复活默认"回归):
 * - 从未配置(null/blank)→ 内置默认
 * - 显式空数组 → 尊重用户(空)
 * - JSON 畸形 → 内置默认
 */
class TitleServiceConfigTest {

    private static TitleService serviceOf(String raw) {
        JdbcTemplate stub = new JdbcTemplate() {
            @Override
            public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
                @SuppressWarnings("unchecked")
                T value = (T) raw;
                return raw == null ? List.of() : List.of(value);
            }
        };
        return new TitleService(new SettingService(stub), stub, null);
    }

    @Test
    void unconfiguredFallsBackToDefaults() {
        assertTrue(serviceOf(null).titles().size() >= 5);
        assertTrue(serviceOf("").achievements().size() >= 5);
    }

    @Test
    void explicitEmptyListIsRespectedNotResurrected() {
        assertTrue(serviceOf("{\"titles\":[]}").titles().isEmpty());
        assertTrue(serviceOf("{\"achievements\":[]}").achievements().isEmpty());
    }

    @Test
    void configuredListIsParsed() {
        var titles = serviceOf("{\"titles\":[{\"code\":\"t1\",\"name\":\"测试\",\"color\":\"#fbbf24\",\"enabled\":true}]}").titles();
        assertEquals(1, titles.size());
        assertEquals("t1", titles.get(0).code());
        var achs = serviceOf("{\"achievements\":[{\"id\":\"a1\",\"name\":\"测试\",\"metric\":\"points_total\",\"target\":10,\"reward\":\"t1\"}]}").achievements();
        assertEquals(1, achs.size());
        assertTrue(achs.get(0).enabled());
    }

    @Test
    void corruptJsonFallsBackToDefaults() {
        assertTrue(serviceOf("not-json{{").titles().size() >= 5);
        assertTrue(serviceOf("not-json{{").achievements().size() >= 5);
    }
}
