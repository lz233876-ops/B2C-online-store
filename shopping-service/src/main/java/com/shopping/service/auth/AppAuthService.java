package com.shopping.service.auth;

import com.shopping.model.dto.AppLoginDTO;
import com.shopping.model.dto.AppRegisterDTO;
import com.shopping.model.vo.AppUserInfoVO;
import com.shopping.model.vo.LoginVO;

/**
 * C 端会员认证服务。
 * <p>
 * 与后台的 {@link AuthService} <b>完全对称但互不相通</b>：
 * 不同的表（{@code ums_user} vs {@code sys_user}）、
 * 不同的 {@code UserDetailsService}、
 * 不同的 {@code AuthenticationManager}、
 * 不同的令牌 scope（{@code APP} vs {@code ADMIN}）。
 * <p>这四层隔离里任何一层单独存在都不够：<b>四层叠加才是「两条独立认证链」。</b>
 */
public interface AppAuthService {

    /** 注册（成功后不自动登录，由前端决定是否立刻调登录） */
    Long register(AppRegisterDTO dto, String clientIp);

    /**
     * 手机号 + 密码登录。
     *
     * @param clientIp 由 Controller 传入，Service 层不碰 {@code HttpServletRequest}
     */
    LoginVO login(AppLoginDTO dto, String clientIp);

    /** 用 refreshToken 换新令牌对（刷新即轮换） */
    LoginVO refresh(String refreshToken);

    /** 登出：拉黑当前 accessToken，并吊销该会员全部 refreshToken */
    void logout(String accessToken);

    /** 当前登录会员信息 */
    AppUserInfoVO getCurrentUserInfo();
}
