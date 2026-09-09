package cn.xmcraft.dreamport.server.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 解析。
 * 修复审计发现：直接取 X-Forwarded-For 第一段可被客户端伪造绕过限流（M4）。
 * 反向代理（nginx 等）会用 $proxy_add_x_forwarded_for 把真实客户端 IP 追加到
 * XFF 末尾，因此取**最后一跳**（而非第一段）才是可信的客户端来源；
 * 未配置代理时退回 socket 地址。
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String realIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String last = hops[hops.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        return request.getRemoteAddr();
    }
}
