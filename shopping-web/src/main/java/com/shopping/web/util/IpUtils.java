package com.shopping.web.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取。
 * <p>
 * <b>为什么不能直接用 {@code request.getRemoteAddr()}：</b>
 * 生产环境前面有 Nginx / 网关，`getRemoteAddr()` 拿到的是<b>代理的 IP</b>，
 * 所有用户的登录日志会变成同一个地址，审计功能直接失效。
 * <p>
 * <b>⚠️ 安全提醒：</b>{@code X-Forwarded-For} 是<b>客户端可以伪造</b>的请求头。
 * 它的正确用法是「只信任可信代理追加的那一段」。标准做法是
 * <b>从右往左</b>读第一个不属于可信代理的地址，而不是从左边取第一个。
 * 这里取最右边的非 unknown 值（列表最后一项由最近一跳代理追加，相对可信）。
 * <p>
 * 由于本项目记录 IP 仅用于审计展示、不做风控决策，这个精度足够。
 * 若要用 IP 做限流或封禁，必须配置可信代理列表后再取。
 */
public final class IpUtils {

    private IpUtils() {
    }

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_V4 = "127.0.0.1";
    private static final String LOCALHOST_V6 = "0:0:0:0:0:0:0:1";
    private static final int MAX_LENGTH = 64;

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        // 优先 X-Forwarded-For，其次 X-Real-IP
        String ip = firstValid(request.getHeader("X-Forwarded-For"));
        if (ip == null) {
            ip = firstValid(request.getHeader("X-Real-IP"));
        }
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        if (LOCALHOST_V6.equals(ip)) {
            ip = LOCALHOST_V4;
        }
        // 落库前截断，防止伪造超长头把 varchar(64) 撑爆
        return ip != null && ip.length() > MAX_LENGTH ? ip.substring(0, MAX_LENGTH) : ip;
    }

    /**
     * {@code X-Forwarded-For} 可能是 {@code 客户端, 代理1, 代理2} 这样的链，
     * 取最后一个（离我们最近的一跳，最难伪造）。
     */
    private static String firstValid(String headerValue) {
        if (headerValue == null || headerValue.isBlank() || UNKNOWN.equalsIgnoreCase(headerValue)) {
            return null;
        }
        String[] parts = headerValue.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (!candidate.isEmpty() && !UNKNOWN.equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
