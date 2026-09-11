package cn.xmcraft.dreamport.server.infra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户自定义 CSS 消毒与作用域隔离单测。
 */
class CssSanitizerTest {

    private static final String SCOPE = "#pc-root";

    @Test
    void 普通规则加作用域前缀() {
        String out = CssSanitizer.sanitize(".card { color: red; }", SCOPE, 8000);
        assertEquals("#pc-root .card { color: red; }", out.trim());
    }

    @Test
    void 多选择器与多条规则() {
        String out = CssSanitizer.sanitize(".a, .b { color: red; }\n.c { background: blue; }", SCOPE, 8000);
        assertTrue(out.contains("#pc-root .a, #pc-root .b"));
        assertTrue(out.contains("#pc-root .c"));
    }

    @Test
    void body与html选择器加前缀后失效() {
        String out = CssSanitizer.sanitize("body { display: none; }", SCOPE, 8000);
        assertEquals("#pc-root body { display: none; }", out.trim());
        // #pc-root body 不可能命中(body 不是 #pc-root 的后代)——沙箱语义成立
    }

    @Test
    void 危险内容被剔除() {
        String out = CssSanitizer.sanitize(
                "@import url('http://evil.com/x.css'); .a { background: url(javascript:alert(1)); }", SCOPE, 8000);
        assertFalse(out.contains("@import"), "@import 必须删除");
        assertFalse(out.contains("javascript:"), "javascript: 必须删除");
    }

    @Test
    void position固定与粘性被剔除() {
        String out = CssSanitizer.sanitize(".a { position: fixed; top: 0; } .b { position: sticky; }", SCOPE, 8000);
        assertFalse(out.contains("fixed"));
        assertFalse(out.contains("sticky"));
    }

    @Test
    void 超长内容截断为空() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 200; i++) big.append(".c").append(i).append(" { color: red; }\n");
        assertEquals("", CssSanitizer.sanitize(big.toString(), SCOPE, 800));
    }

    @Test
    void 空输入返回空() {
        assertEquals("", CssSanitizer.sanitize(null, SCOPE, 8000));
        assertEquals("", CssSanitizer.sanitize("   ", SCOPE, 8000));
    }
}
