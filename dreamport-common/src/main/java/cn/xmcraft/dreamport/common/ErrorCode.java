package cn.xmcraft.dreamport.common;

/**
 * 内部协议错误码（沿用旧版/AstrBot 适配器的分段习惯：
 * 1xxx 认证、2xxx 参数、3xxx 服务端、4xxx 资源、5xxx 命令）。
 */
public enum ErrorCode {
    OK(0, "成功"),
    AUTH_FAILED(1001, "服务器认证失败"),
    AUTH_EXPIRED(1002, "服务器令牌已过期"),
    TOKEN_MISSING(1003, "缺少服务器令牌"),
    BAD_REQUEST(2001, "请求参数错误"),
    INTERNAL_ERROR(3001, "服务端内部错误"),
    SERVICE_UNAVAILABLE(3002, "服务暂不可用"),
    NOT_FOUND(4001, "资源不存在"),
    USER_NOT_FOUND(4002, "用户不存在"),
    FEATURE_DISABLED(4003, "功能未启用"),
    COMMAND_FAILED(5001, "指令执行失败"),
    COMMAND_FILTERED(5002, "指令被过滤"),
    COMMAND_FORBIDDEN(5003, "无权执行指令");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
