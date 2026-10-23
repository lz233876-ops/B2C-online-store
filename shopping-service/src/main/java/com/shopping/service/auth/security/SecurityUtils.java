package com.shopping.service.auth.security;

import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring Security 上下文里取当前登录用户的工具。
 * <p>
 * 为什么要有它：Service 层不应接收 {@code HttpServletRequest}（那样 service 就依赖 web 了，
 * 会被 Maven Enforcer 拦下）。当前用户在 {@code SecurityContextHolder} 里，
 * 而 {@code SecurityContextHolder} 默认使用 {@code ThreadLocal} 策略，
 * 所以同一请求线程内的任何一层都能取到。
 * <p>
 * ⚠️ <b>异步/多线程场景下取不到</b>：{@code ThreadLocal} 不跨线程。
 * 若在 {@code @Async} 或线程池里需要用户信息，必须在提交任务前把
 * userId 显式传进去（推荐），或设置
 * {@code SecurityContextHolder.setStrategyName(MODE_INHERITABLETHREADLOCAL)}
 * 并使用 {@code InheritableThreadLocal}（注意线程池复用会导致信息串线，不推荐）。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 取当前登录用户。
     *
     * @return 未登录时返回 {@code null}
     */
    public static LoginUser getLoginUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            return null;
        }
        return loginUser;
    }

    /**
     * 取当前登录用户，未登录直接抛业务异常。
     * <p>
     * 用在「过滤器已经保证登录」的接口里，省掉每处 null 判断。
     */
    public static LoginUser getLoginUser() {
        LoginUser loginUser = getLoginUserOrNull();
        if (loginUser == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    public static String getUsername() {
        return getLoginUser().getUsername();
    }

    /* ==================== C 端会员 ==================== */

    /**
     * 取当前登录会员。
     * <p>
     * <b>为什么不像后台那样做「按类型强转」：</b>
     * 两个主体的类型不同（{@code LoginUser} / {@code AppLoginUser}），
     * 而 {@code SecurityContext} 里只有一个 principal。这里用 {@code instanceof} 判断类型，
     * 天然实现了隔离：<b>后台令牌进来的请求，在这里永远取不到会员</b>，
     * 反之亦然。所以「用后台 token 调 C 端会员接口」会得到 401 而不是误操作到某个会员。
     */
    public static AppLoginUser getAppLoginUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppLoginUser appLoginUser)) {
            return null;
        }
        return appLoginUser;
    }

    public static AppLoginUser getAppLoginUser() {
        AppLoginUser appLoginUser = getAppLoginUserOrNull();
        if (appLoginUser == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return appLoginUser;
    }

    /** 当前登录会员 ID。收货地址等「按用户隔离」的数据一律用它，绝不从入参取 */
    public static Long getAppUserId() {
        return getAppLoginUser().getUserId();
    }
}
