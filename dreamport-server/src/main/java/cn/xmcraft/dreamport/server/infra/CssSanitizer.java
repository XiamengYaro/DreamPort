package cn.xmcraft.dreamport.server.infra;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户自定义 CSS 消毒与作用域隔离(个人主页装修):
 * - 删除 @import/@charset/@namespace 及一切顶层 @ 规则(外链加载、未知副作用)
 * - 删除 javascript:/expression(/behavior/-moz-binding(脚本类)
 * - 删除 position: fixed/sticky(防止用户元素悬浮覆盖站点导航/页脚)
 * - 每条规则的选择器加作用域前缀(scope + 空格),CSS 只作用于包裹容器内部;
 *   body/html 等外部选择器加前缀后不再命中,天然失效
 * - 结果超过 maxLen 返回 ""(宁缺毋滥)
 */
public final class CssSanitizer {

    private static final Pattern DANGEROUS = Pattern.compile(
            "@import|@charset|@namespace|javascript:|expression\\s*\\(|behavior\\s*:|-moz-binding|<\\s*script",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITION_FIXED = Pattern.compile(
            "position\\s*:\\s*(fixed|sticky)\\s*[;}]?", Pattern.CASE_INSENSITIVE);

    private CssSanitizer() {
    }

    public static String sanitize(String css, String scope, int maxLen) {
        if (css == null || css.isBlank() || scope == null || scope.isBlank()) {
            return "";
        }
        String cleaned = DANGEROUS.matcher(css).replaceAll("");
        cleaned = POSITION_FIXED.matcher(cleaned).replaceAll("");
        String scoped = scopeRules(cleaned.trim(), scope.trim());
        return scoped.length() > maxLen ? "" : scoped;
    }

    /** 逐条规则给选择器加作用域前缀;支持逗号分隔的多选择器 */
    static String scopeRules(String css, String scope) {
        StringBuilder out = new StringBuilder();
        for (String rule : splitRules(css)) {
            int brace = rule.indexOf('{');
            if (brace <= 0) {
                continue;
            }
            String body = rule.substring(brace);
            if (body.length() < 3) {
                continue;
            }
            StringBuilder scopedSelectors = new StringBuilder();
            for (String sel : rule.substring(0, brace).split(",")) {
                String trimmed = sel.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (scopedSelectors.length() > 0) {
                    scopedSelectors.append(", ");
                }
                scopedSelectors.append(scope).append(' ').append(trimmed);
            }
            if (scopedSelectors.length() > 0) {
                out.append(scopedSelectors).append(' ').append(body).append('\n');
            }
        }
        return out.toString();
    }

    /** 按顶层花括号切分规则(跳过空白;不配平的输入忽略) */
    static List<String> splitRules(String css) {
        List<String> rules = new ArrayList<>();
        int i = 0;
        while (i < css.length()) {
            while (i < css.length() && Character.isWhitespace(css.charAt(i))) i++;
            if (i >= css.length()) break;
            int brace = css.indexOf('{', i);
            if (brace < 0) break;
            int close = css.indexOf('}', brace);
            if (close < 0) break;
            rules.add(css.substring(i, close + 1));
            i = close + 1;
        }
        return rules;
    }
}
