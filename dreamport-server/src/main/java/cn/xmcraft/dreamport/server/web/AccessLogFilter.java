package cn.xmcraft.dreamport.server.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 网站访问日志（终端实时输出）：[访问] 玩家资料 Xia_Meng_ 200 13ms 1.2.3.4 @Xia_Meng_
 * 常用接口按中文描述输出；未知接口将 HTTP 动词中文化后保留路径。
 * 覆盖 /api/** 与 /internal/**；静态资源不记录。
 */
@Component
@Order(2)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("access");

    /** 常用接口路由 → 中文描述；带尾斜杠的做前缀匹配并追加剩余路径(如玩家名) */
    private static final String[][] ROUTES = {
            {"/api/login", "登录"},
            {"/api/register", "注册"},
            {"/api/health", "健康检查"},
            {"/api/config", "站点配置"},
            {"/api/auth/validate", "校验登录令牌"},
            {"/api/players/list", "查询玩家列表"},
            {"/api/players/search", "搜索玩家"},
            {"/api/user/score", "我的评分"},
            {"/api/user/profile-custom", "主页自定义"},
            {"/api/astrbot/stream", "AstrBot 消息流(长轮询)"},
            {"/api/upload/image", "图片上传"},
            {"/internal/v1/messages/pending", "插件轮询·待处理消息"},
            {"/internal/v1/commands/whitelist", "插件拉取·白名单指令"},
            {"/internal/v1/commands/ban", "插件拉取·封禁指令"},
            {"/api/players/profile/", "玩家资料 "},
            {"/api/friends/status/", "好友状态 "},
            {"/api/avatar/", "头像 "},
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            String uri = request.getRequestURI();
            if (uri.startsWith("/api/") || uri.startsWith("/internal/")) {
                String ip = ClientIp.realIp(request);
                String who = String.valueOf(request.getAttribute("wl.username"));
                log.info("[访问] {} {} {}ms {}{}",
                        describe(request.getMethod(), uri), response.getStatus(),
                        System.currentTimeMillis() - start, ip,
                        "null".equals(who) ? "" : " @" + who);
            }
        }
    }

    private static String describe(String method, String uri) {
        for (String[] r : ROUTES) {
            if (uri.startsWith(r[0])) {
                return uri.length() > r[0].length() && r[0].endsWith("/")
                        ? r[1] + uri.substring(r[0].length())
                        : r[1];
            }
        }
        String verb = switch (method) {
            case "GET" -> "读取";
            case "POST" -> "提交";
            case "PUT" -> "更新";
            case "DELETE" -> "删除";
            case "PATCH" -> "修改";
            default -> method;
        };
        return verb + " " + uri;
    }
}
