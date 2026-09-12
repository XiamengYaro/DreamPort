package cn.xmcraft.dreamport.server.seo;

import cn.xmcraft.dreamport.server.settings.SettingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 服务端 SEO meta 注入:SPA 的 index.html 由后端直出,爬虫不执行 JS 也能拿到
 * 标题/描述/关键词/Open Graph(按请求 URI 映射页面名,私有页自动 noindex);
 * extraHead 为管理员自定义注入(站点验证码/统计脚本等)。
 * 仅重写 text/html 响应;/api、/internal、/assets、/uploads、robots.txt、sitemap.xml 不经过注入。
 */
@Component
@Order(2)
public class SeoMetaInjectionFilter extends OncePerRequestFilter {

    private final SettingService settingService;

    public SeoMetaInjectionFilter(SettingService settingService) {
        this.settingService = settingService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean excluded = uri.startsWith("/api/") || uri.startsWith("/internal/") || uri.startsWith("/assets/")
                || uri.startsWith("/uploads/") || uri.equals("/robots.txt") || uri.equals("/sitemap.xml");
        boolean htmlCandidate = !excluded
                && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()));
        if (!htmlCandidate) {
            chain.doFilter(request, response);
            return;
        }
        CaptureResponseWrapper wrapper = new CaptureResponseWrapper(response);
        chain.doFilter(request, wrapper);
        byte[] body = wrapper.body();
        String contentType = wrapper.getContentType();
        if (contentType == null || !contentType.contains("text/html") || body.length == 0) {
            wrapper.writeBodyTo(response);
            return;
        }
        String html = new String(body, StandardCharsets.UTF_8);
        if (!html.contains("<head")) {
            wrapper.writeBodyTo(response);
            return;
        }
        Map<String, Object> cfg = settingService.getMap(SettingService.KEY_SEO_CONFIG);
        Map<String, Object> portal = settingService.getMap(SettingService.KEY_PORTAL);
        String site = SeoSupport.str(portal.getOrDefault("server_name", "夏日小镇★XMCraft"));
        String page = SeoSupport.pageName(uri);
        boolean noindex = SeoSupport.isNoindexPath(uri) || "noindex".equals(cfg.get("robots"));
        String head = SeoSupport.buildMetaBlock(page, site, cfg, noindex);
        String extra = SeoSupport.str(cfg.get("extraHead"));
        if (!extra.isBlank()) {
            head = head + extra + "\n";
        }
        String title = SeoSupport.renderTitle(SeoSupport.str(cfg.get("titleTemplate")), page, site);
        String injected = SeoSupport.inject(html, head, title);
        byte[] out = injected.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(out.length);
        response.getOutputStream().write(out);
    }

    /** 捕获响应体,便于注入后整体重写 */
    private static final class CaptureResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        private final ServletOutputStream stream = new ServletOutputStream() {
            @Override
            public void write(int b) {
                bos.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                bos.write(b, off, len);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };
        private final PrintWriter writer = new PrintWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8));

        CaptureResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return stream;
        }

        @Override
        public PrintWriter getWriter() {
            return writer;
        }

        byte[] body() {
            writer.flush();
            return bos.toByteArray();
        }

        void writeBodyTo(HttpServletResponse resp) throws IOException {
            resp.setContentLength(bos.size());
            bos.writeTo(resp.getOutputStream());
        }
    }
}
