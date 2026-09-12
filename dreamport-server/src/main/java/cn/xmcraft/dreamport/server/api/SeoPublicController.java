package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.docs.DocsService;
import cn.xmcraft.dreamport.server.seo.SeoSupport;
import cn.xmcraft.dreamport.server.settings.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 爬虫可见的 SEO 端点:
 * - /robots.txt:robots=index 时放行并附 sitemap;noindex 时全站 Disallow(私有部署用)
 * - /sitemap.xml:公开静态路由 + 文档详情页(/docs/{filename})
 * 精确路径映射优先于 SPA 视图控制器回退。
 */
@RestController
public class SeoPublicController {

    private final SettingService settingService;
    private final DocsService docsService;

    public SeoPublicController(SettingService settingService, DocsService docsService) {
        this.settingService = settingService;
        this.docsService = docsService;
    }

    @GetMapping(value = "/robots.txt", produces = "text/plain;charset=UTF-8")
    public String robots(HttpServletRequest request) {
        boolean index = indexed();
        return SeoSupport.buildRobotsTxt(index, index ? baseUrl(request) + "/sitemap.xml" : null);
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public String sitemap(HttpServletRequest request) {
        List<String> paths = new ArrayList<>(SeoSupport.SITEMAP_PATHS);
        if (indexed()) {
            try {
                Map<String, Object> all = docsService.listAll();
                Object cats = all.get("categories");
                if (cats instanceof List<?> list) {
                    for (Object c : list) {
                        if (c instanceof Map<?, ?> m && m.get("docs") instanceof List<?> docs) {
                            for (Object d : docs) {
                                if (d instanceof Map<?, ?> dm && dm.get("filename") != null) {
                                    paths.add("/docs/" + dm.get("filename"));
                                }
                            }
                        }
                    }
                }
                Object unc = all.get("uncategorized");
                if (unc instanceof List<?> list) {
                    for (Object d : list) {
                        if (d instanceof Map<?, ?> dm && dm.get("filename") != null) {
                            paths.add("/docs/" + dm.get("filename"));
                        }
                    }
                }
            } catch (Exception ignore) {
                // 文档目录不可用时 sitemap 仅含静态路由
            }
        }
        return SeoSupport.buildSitemapXml(indexed() ? baseUrl(request) : "", indexed() ? paths : List.of());
    }

    private boolean indexed() {
        return !"noindex".equals(settingService.getMap(SettingService.KEY_SEO_CONFIG).get("robots"));
    }

    /** 站点根 URL:优先反向代理头(X-Forwarded-Proto/Host),否则取请求自身 */
    private String baseUrl(HttpServletRequest req) {
        String proto = req.getHeader("X-Forwarded-Proto");
        String host = req.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = req.getServerName();
        }
        if (proto == null || proto.isBlank()) {
            proto = req.getScheme();
        }
        int port = req.getServerPort();
        StringBuilder sb = new StringBuilder(proto).append("://").append(host);
        boolean defaultPort = ("http".equals(proto) && port == 80) || ("https".equals(proto) && port == 443);
        if (!defaultPort && !host.contains(":")) {
            sb.append(':').append(port);
        }
        return sb.toString();
    }
}
