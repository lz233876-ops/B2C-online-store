package com.shopping.service.auth;

import com.shopping.service.auth.security.AppLoginUser;

/**
 * 按会员 ID 装载 C 端登录主体（供 C 端 JWT 过滤器每次请求调用）。
 * <p>与后台的 {@link LoginUserService} 对称，但返回不同的主体类型。
 */
public interface AppLoginUserService {

    /**
     * @throws com.shopping.common.exception.BizException 会员不存在
     */
    AppLoginUser loadByUserId(Long userId);
}
