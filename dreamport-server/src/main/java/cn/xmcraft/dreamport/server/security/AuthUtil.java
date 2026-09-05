package cn.xmcraft.dreamport.server.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 控制器侧鉴权判定工具（读取 AuthFilter 写入的请求属性）。
 */
public final class AuthUtil {

    public static final String ATTR_USERNAME = "wl.username";
    public static final String ATTR_ROLE = "wl.role";

    private AuthUtil() {
    }

    /** 当前登录用户名；未登录返回 null */
    public static String currentUser(HttpServletRequest request) {
        return (String) request.getAttribute(ATTR_USERNAME);
    }

    /** 当前角色；未登录返回 null */
    public static String currentRole(HttpServletRequest request) {
        return (String) request.getAttribute(ATTR_ROLE);
    }

    public static boolean isAdmin(HttpServletRequest request) {
        return TokenService.ROLE_ADMIN.equals(currentRole(request));
    }
}
