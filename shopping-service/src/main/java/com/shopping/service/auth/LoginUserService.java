package com.shopping.service.auth;

import com.shopping.service.auth.security.LoginUser;

/**
 * 按用户 ID 装配 {@link LoginUser}。
 * <p>
 * 为什么不复用 {@code UserDetailsService.loadUserByUsername}：
 * JWT 里带的是 <b>userId</b>（稳定不变），而 username 是可以被管理员修改的。
 * 如果按 username 装载，用户改名后所有已签发的令牌会立刻失效——
 * 这虽然不是安全问题，但会造成莫名其妙的「被登出」。
 * <p>
 * 所以：<b>登录时按 username 认证，之后按 userId 装载。</b>
 */
public interface LoginUserService {

    /**
     * 按用户 ID 组装登录主体（含角色与权限）。
     *
     * @throws com.shopping.common.exception.BizException 用户不存在或被删除
     */
    LoginUser loadByUserId(Long userId);
}
