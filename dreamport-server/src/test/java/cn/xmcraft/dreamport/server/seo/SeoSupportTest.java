package cn.xmcraft.dreamport.server.seo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SEO 纯函数工具:标题模板渲染、页面名映射、meta 注入转义、robots/sitemap 构建。
 */
class SeoSupportTest {

    @Test
    void 标题模板渲染与连接符清理() {
        assertEquals("公告中心 - 夏日小镇", SeoSupport.renderTitle("{page} - {site}", "公告中心", "夏日小镇"));
        // 首页 page 为空:去连接符后只留站名
        assertEquals("夏日小镇", SeoSupport.renderTitle("{page} - {site}", "", "夏日小镇"));
        assertEquals("夏日小镇 · 玩家目录", SeoSupport.renderTitle("{site} · {page}", "玩家目录", "夏日小镇"));
        assertEquals("夏日小镇", SeoSupport.renderTitle(null, "", "夏日小镇"));
    }

    @Test
    void 页面名映射与私有页noindex() {
        assertEquals("公告中心", SeoSupport.pageName("/announcements"));
        assertEquals("玩家档案", SeoSupport.pageName("/player/Xiameng"));
        assertEquals("文档中心", SeoSupport.pageName("/docs/01-规则/白名单"));
        assertEquals("", SeoSupport.pageName("/"));
        assertTrue(SeoSupport.isNoindexPath("/admin"));
        assertTrue(SeoSupport.isNoindexPath("/dashboard"));
        assertFalse(SeoSupport.isNoindexPath("/questionnaire-result"), "公开的问卷结果页不得被误伤");
        assertFalse(SeoSupport.isNoindexPath("/announcements"));
    }

    @Test
    void meta块生成带转义且注入head唯一title() {
        Map<String, Object> seo = Map.of(
                "titleTemplate", "{page} - {site}",
                "description", "描述\"引号\"与<标签>",
                "keywords", "mc,生存",
                "ogImage", "/Logo111.png");
        String block = SeoSupport.buildMetaBlock("文档中心", "夏日小镇", seo, false);
        assertTrue(block.contains("content=\"描述&quot;引号&quot;与&lt;标签&gt;\""), "属性值需 HTML 转义");
        assertTrue(block.contains("index, follow"));
        assertTrue(block.contains("property=\"og:image\" content=\"/Logo111.png\""));

        String html = "<html><head><title>DreamPort</title><meta charset=\"UTF-8\"></head><body></body></html>";
        String injected = SeoSupport.inject(html, block, "文档中心 - 夏日小镇");
        assertTrue(injected.contains("<title>文档中心 - 夏日小镇</title>"), "原 title 被替换");
        assertEquals(1, injected.split("<title>", -1).length - 1, "title 只保留一份");
        assertTrue(injected.indexOf("name=\"description\"") < injected.indexOf("</head>"), "meta 注入在 head 内");
        assertTrue(injected.contains("<meta charset=\"UTF-8\">"), "原有 head 内容保留");
    }

    @Test
    void robots与sitemap构建() {
        String robots = SeoSupport.buildRobotsTxt(true, "http://x/sitemap.xml");
        assertTrue(robots.contains("Allow: /") && robots.contains("Sitemap: http://x/sitemap.xml"));
        assertTrue(SeoSupport.buildRobotsTxt(false, null).contains("Disallow: /"));
        assertFalse(SeoSupport.buildRobotsTxt(false, null).contains("Sitemap:"));

        String xml = SeoSupport.buildSitemapXml("http://x", List.of("/", "/docs", "/docs/a.md", "/docs"));
        assertTrue(xml.contains("<loc>http://x/docs/a.md</loc>"));
        assertTrue(xml.contains("<loc>http://x/</loc>"));
        assertEquals(3, xml.split("<url>", -1).length - 1, "重复路径去重");
    }
}
