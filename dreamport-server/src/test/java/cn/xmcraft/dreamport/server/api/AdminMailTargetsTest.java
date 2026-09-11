package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.user.UserRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮件群发目标收集:邮箱非空/合法过滤 + 小写去重。
 */
class AdminMailTargetsTest {

    private static UserRecord user(String username, String email) {
        // 只用到 email 字段,其余 28 个字段置空
        return new UserRecord(null, username, email, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    @Test
    void 过滤空与非法邮箱并小写去重() {
        List<UserRecord> users = List.of(
                user("a", "A@Example.COM"),
                user("b", "a@example.com"),      // 与上面同邮箱(大小写不同)→ 去重
                user("c", ""),
                user("d", null),
                user("e", "not-an-email"),
                user("f", "  bob@x.com  "),      // 首尾空格 → trim
                user("g", "bob@x.com"));         // trim 后同 bob@x.com → 去重
        List<String> targets = AdminMailController.collectTargets(users);
        assertEquals(2, targets.size());
        assertTrue(targets.contains("a@example.com"));
        assertTrue(targets.contains("bob@x.com"));
    }

    @Test
    void 无合法邮箱时为空() {
        assertTrue(AdminMailController.collectTargets(List.of(
                user("x", ""), user("y", "bad"))).isEmpty());
    }
}
