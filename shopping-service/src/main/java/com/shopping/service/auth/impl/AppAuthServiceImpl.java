package com.shopping.service.auth.impl;

import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.common.util.MaskUtils;
import com.shopping.model.dto.AppLoginDTO;
import com.shopping.model.dto.AppRegisterDTO;
import com.shopping.model.entity.UmsUser;
import com.shopping.model.vo.AppUserInfoVO;
import com.shopping.model.vo.LoginVO;
import com.shopping.service.auth.AppAuthService;
import com.shopping.service.auth.TokenService;
import com.shopping.service.auth.security.AppLoginUser;
import com.shopping.service.auth.security.SecurityUtils;
import com.shopping.service.user.UmsUserService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * C 端会员认证实现。
 * <p>
 * <b>{@code @Qualifier("appAuthenticationManager")} 是本类最关键的一行。</b>
 * 容器里有两个 {@code AuthenticationManager}，不指定就会因「候选 Bean 不唯一」启动失败；
 * 更重要的是：如果误用了后台那个，会员账号的密码会拿去 {@code sys_user} 表里比对 → 永远登不上，
 * 或者更糟——如果两边恰好有同名账号，就会出现跨体系登录。
 */
@Slf4j
@Service
public class AppAuthServiceImpl implements AppAuthService {

    private final AuthenticationManager appAuthenticationManager;
    private final TokenService tokenService;
    private final UmsUserService umsUserService;

    public AppAuthServiceImpl(@Qualifier("appAuthenticationManager") AuthenticationManager appAuthenticationManager,
                              TokenService tokenService,
                              UmsUserService umsUserService) {
        this.appAuthenticationManager = appAuthenticationManager;
        this.tokenService = tokenService;
        this.umsUserService = umsUserService;
    }

    @Override
    public Long register(AppRegisterDTO dto, String clientIp) {
        return umsUserService.register(dto, clientIp);
    }

    @Override
    public LoginVO login(AppLoginDTO dto, String clientIp) {
        Authentication authentication;
        try {
            // 走 C 端专属的认证管理器：它只认 UmsUserDetailsService（查 ums_user）
            authentication = appAuthenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getPhone(), dto.getPassword()));
        } catch (DisabledException e) {
            log.warn("会员登录失败：账号已禁用 | phone={}", MaskUtils.phone(dto.getPhone()));
            throw new BizException(ResultCode.MEMBER_DISABLED);
        } catch (BadCredentialsException e) {
            // 「手机号不存在」与「密码错误」返回同一句提示，避免手机号枚举
            log.warn("会员登录失败：凭据错误 | phone={} | ip={}", MaskUtils.phone(dto.getPhone()), clientIp);
            throw new BizException(ResultCode.LOGIN_FAILED);
        } catch (AuthenticationException e) {
            log.warn("会员登录失败：{} | phone={}", e.getClass().getSimpleName(), MaskUtils.phone(dto.getPhone()));
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        AppLoginUser loginUser = (AppLoginUser) authentication.getPrincipal();
        umsUserService.updateLoginInfo(loginUser.getUserId(), clientIp);

        log.info("会员登录成功 | userId={} | phone={} | ip={}",
                loginUser.getUserId(), MaskUtils.phone(loginUser.getPhone()), clientIp);
        // 用手机号作为令牌里的 username（登录标识）
        return tokenService.createTokenPair(loginUser.getUserId(), loginUser.getPhone(), TokenService.SCOPE_APP);
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        Claims claims = tokenService.parse(refreshToken);

        if (!TokenService.TYPE_REFRESH.equals(tokenService.getTokenType(claims))) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        // ⚠️ 必须校验 scope：否则拿【后台的 refreshToken】也能在 C 端换出新令牌，
        //    两条链的隔离就被打破了。这是很容易漏掉的一步。
        if (!TokenService.SCOPE_APP.equals(tokenService.getUserScope(claims))) {
            log.warn("C 端刷新失败：令牌 scope 不是 APP");
            throw new BizException(ResultCode.TOKEN_INVALID);
        }

        Long userId = tokenService.getUserId(claims);
        String jti = tokenService.getJti(claims);
        if (!tokenService.isRefreshTokenActive(TokenService.SCOPE_APP, userId, jti)) {
            throw new BizException(ResultCode.TOKEN_EXPIRED);
        }
        tokenService.removeRefreshToken(TokenService.SCOPE_APP, userId, jti);

        UmsUser user = umsUserService.getRequiredById(userId);
        return tokenService.createTokenPair(userId, user.getPhone(), TokenService.SCOPE_APP);
    }

    @Override
    public void logout(String accessToken) {
        AppLoginUser loginUser = SecurityUtils.getAppLoginUserOrNull();
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                tokenService.blacklistAccessToken(tokenService.parse(accessToken));
            } catch (BizException e) {
                log.debug("C 端登出时令牌已失效，忽略 | reason={}", e.getMessage());
            }
        }
        if (loginUser != null) {
            tokenService.removeAllRefreshTokens(TokenService.SCOPE_APP, loginUser.getUserId());
            log.info("会员登出成功 | userId={}", loginUser.getUserId());
        }
    }

    @Override
    public AppUserInfoVO getCurrentUserInfo() {
        AppLoginUser loginUser = SecurityUtils.getAppLoginUser();
        // 积分/等级等会变的字段从库里读实时值，不放进令牌
        UmsUser user = umsUserService.getRequiredById(loginUser.getUserId());
        return AppUserInfoVO.builder()
                .userId(user.getId())
                .phone(MaskUtils.phone(user.getPhone()))
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .username(user.getUsername())
                .gender(user.getGender())
                .memberLevelId(user.getMemberLevelId())
                .growth(user.getGrowth())
                .points(user.getPoints())
                .build();
    }
}
