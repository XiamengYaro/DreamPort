package cn.xmcraft.dreamport.server.seo;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * SEO 纯函数工具(单测覆盖):页面名映射、标题模板渲染、meta 块生成、HTML head 注入、robots.txt 与 sitemap.xml 构建。
 * 配置来自 dp_setting 的 seo.config:{titleTemplate, description, keywords, ogImage, robots: index|noindex, extraHead}。
 */
public final class SeoSupport {

    private SeoSupport() {
    }

    public static final String DEFAULT_TITLE_TEMPLATE = "{page} - {site}";

    /** 公开页 URI 前缀 → 页面名(服务端注入用;最长前缀优先) */
    private static final String[][] PAGE_TITLES = {
            {"/announcements", "公告中心"},
            {"/leaderboard", "排行榜"},
            {"/players", "玩家目录"},
            {"/player/", "玩家档案"},
            {"/village", "村民族谱"},
            {"/machines", "公共机器"},
            {"/community", "社区"},
            {"/docs", "文档中心"},
            {"/bans", "封禁公示"},
            {"/chat", "聊天广场"},
            {"/tasks", "任务中心"},
            {"/whitelist", "加入白名单"},
            {"/questionnaire-result", "问卷结果"},
            {"/status", "服务器状态"},
            {"/map", "服务器地图"},
            {"/login", "登录"},
            {"/register", "注册"},
            {"/forgot-password", "找回密码"},
            {"/reset-password", "重置密码"},
    };

    /** 私有页前缀 → noindex(无公开内容价值:后台/控制台/验证/答题等) */
    private static final List<String> NOINDEX_PREFIXES = List.of(
            "/admin", "/dashboard", "/verify", "/questionnaire", "/setup");

    /** sitemap 收录的公开静态路由 */
    public static final List<String> SITEMAP_PATHS = List.of(
            "/", "/docs", "/whitelist", "/announcements", "/bans", "/players", "/leaderboard",
            "/village", "/machines", "/community", "/chat", "/status", "/tasks", "/map");

    /** URI → 页面名(最长前缀优先;未匹配返回空串=站点首页) */
    public static String pageName(String uri) {
        if (uri == null || uri.isBlank() || "/".equals(uri) || "/index.html".equals(uri)) {
            return "";
        }
        String best = "";
        String name = "";
        for (String[] p : PAGE_TITLES) {
            if (uri.equals(p[0]) || uri.startsWith(p[0])) {
                if (p[0].length() > best.length()) {
                    best = p[0];
                    name = p[1];
                }
            }
        }
        return name;
    }

    /** 私有页(无 SEO 价值)→ noindex;前缀匹配但不含 /questionnaire-result 这类公开页 */
    public static boolean isNoindexPath(String uri) {
        if (uri == null) return false;
        return NOINDEX_PREFIXES.stream()
                .anyMatch(p -> uri.equals(p) || uri.startsWith(p + "/") || uri.startsWith(p + "?"));
    }

    /** 标题模板渲染:{page}/{site} 占位;结果去首尾连接符 */
    public static String renderTitle(String template, String page, String site) {
        String t = template == null || template.isBlank() ? DEFAULT_TITLE_TEMPLATE : template;
        String out = t.replace("{page}", page == null ? "" : page)
                .replace("{site}", site == null ? "" : site);
        out = out.replaceAll("^[\\s\\-–—·|]+", "").replaceAll("[\\s\\-–—·|]+$", "").trim();
        return out.isBlank() ? (site == null ? "" : site) : out;
    }

    /** meta 块(不含 title;title 由 inject 单独替换) */
    public static String buildMetaBlock(String page, String site, Map<String, Object> seo, boolean noindex) {
        String title = renderTitle(str(seo.get("titleTemplate")), page, site);
        String desc = str(seo.get("description"));
        String kw = str(seo.get("keywords"));
        String og = str(seo.get("ogImage"));
        StringBuilder sb = new StringBuilder();
        if (!desc.isBlank()) {
            sb.append("<meta name=\"description\" content=\"").append(esc(desc)).append("\">\n");
        }
        if (!kw.isBlank()) {
            sb.append("<meta name=\"keywords\" content=\"").append(esc(kw)).append("\">\n");
        }
        sb.append("<meta name=\"robots\" content=\"").append(noindex ? "noindex, nofollow" : "index, follow").append("\">\n");
        sb.append("<meta property=\"og:type\" content=\"website\">\n");
        sb.append("<meta property=\"og:site_name\" content=\"").append(esc(site)).append("\">\n");
        sb.append("<meta property=\"og:title\" content=\"").append(esc(title)).append("\">\n");
        if (!desc.isBlank()) {
            sb.append("<meta property=\"og:description\" content=\"").append(esc(desc)).append("\">\n");
        }
        if (!og.isBlank()) {
            sb.append("<meta property=\"og:image\" content=\"").append(esc(og)).append("\">\n");
        }
        return sb.toString();
    }

    /** 将 meta 块与标题注入 HTML:原 <title> 被替换,其余插到 </head> 前 */
    public static String inject(String html, String headTags, String title) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        String out = html;
        String titleTag = "<title>" + esc(title) + "</title>";
        boolean replaced = false;
        if (out.contains("<title>")) {
            out = out.replaceAll("<title>[\\s\\S]*?</title>", Matcher.quoteReplacement(titleTag));
            replaced = true;
        }
        String block = replaced ? headTags : (headTags.isBlank() ? titleTag : titleTag + "\n" + headTags);
        if (out.contains("</head>")) {
            out = out.replace("</head>", block + "</head>");
        } else {
            out = out + block;
        }
        return out;
    }

    public static String buildRobotsTxt(boolean index, String sitemapUrl) {
        StringBuilder sb = new StringBuilder("User-agent: *\n");
        sb.append(index ? "Allow: /\n" : "Disallow: /\n");
        if (index && sitemapUrl != null && !sitemapUrl.isBlank()) {
            sb.append("\nSitemap: ").append(sitemapUrl.trim()).append('\n');
        }
        return sb.toString();
    }

    public static String buildSitemapXml(String baseUrl, List<String> paths) {
        Set<String> seen = new LinkedHashSet<>();
        if (paths != null) {
            paths.stream().filter(p -> p != null && !p.isBlank()).forEach(seen::add);
        }
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String p : seen) {
            String loc = (baseUrl == null ? "" : baseUrl) + (p.startsWith("/") ? p : "/" + p);
            sb.append("  <url><loc>").append(esc(loc)).append("</loc></url>\n");
        }
        return sb.append("</urlset>\n").toString();
    }

    /** 配置缺省补齐(admin UI 与公开输出共用一套键) */
    public static Map<String, Object> withDefaults(Map<String, Object> cfg, String siteName, String portalDescription) {
        Map<String, Object> out = cfg == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cfg);
        out.putIfAbsent("titleTemplate", DEFAULT_TITLE_TEMPLATE);
        out.putIfAbsent("description", portalDescription == null ? "" : portalDescription);
        out.putIfAbsent("keywords", "");
        out.putIfAbsent("ogImage", "");
        out.putIfAbsent("robots", "index");
        out.putIfAbsent("extraHead", "");
        if (siteName != null) {
            out.putIfAbsent("siteName", siteName);
        }
        return out;
    }

    public static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
