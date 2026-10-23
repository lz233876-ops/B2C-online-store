package com.shopping.service.auth.impl;

import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.common.util.MaskUtils;
import com.shopping.model.dto.LoginDTO;
import com.shopping.model.entity.SysUser;
import com.shopping.model.vo.AdminInfoVO;
import com.shopping.model.vo.LoginVO;
import com.shopping.model.vo.MenuTreeVO;
import com.shopping.service.auth.AuthService;
import com.shopping.service.auth.TokenService;
import com.shopping.service.auth.security.LoginUser;
import com.shopping.service.auth.security.SecurityUtils;
import com.shopping.service.system.SysPermissionService;
import com.shopping.service.system.SysUserService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 后台认证服务实现。
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager adminAuthenticationManager;
    private final TokenService tokenService;
    private final SysUserService sysUserService;
    private final SysPermissionService sysPermissionService;

    /**
     * 显式构造器 + {@code @Qualifier}：容器里有两个 {@code AuthenticationManager}
     * （后台的与 C 端会员的），不指定就不知道注入哪一个。
     * <p>这里<b>刻意不用 {@code @RequiredArgsConstructor}</b>——字段上的
     * {@code @Qualifier} 能否被 Lombok 复制到构造器参数，取决于
     * {@code lombok.copyableAnnotations} 配置，隐式依赖太脆弱。
     * 认证链选错是安全事故级别的 bug，值得多写这 8 行。
     */
    public AuthServiceImpl(@Qualifier("adminAuthenticationManager") AuthenticationManager adminAuthenticationManager,
                           TokenService tokenService,
                           SysUserService sysUserService,
                           SysPermissionService sysPermissionService) {
        this.adminAuthenticationManager = adminAuthenticationManager;
        this.tokenService = tokenService;
        this.sysUserService = sysUserService;
        this.sysPermissionService = sysPermissionService;
    }

    /**
     * 登录。
     * <p>
     * 走 {@code AuthenticationManager} 而不是「自己查库 + 自己 compare 密码」，
     * 好处是：账号禁用、密码校验、凭据过期等规则全部由 Spring Security 统一处理，
     * 且任何走这条链的入口都自动遵守同一套规则。
     */
    @Override
    public LoginVO login(LoginDTO dto, String clientIp) {
        Authentication authentication;
        try {
            authentication = adminAuthenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));
        } catch (DisabledException e) {
            // 账号被禁用是明确的业务状态，可以告诉用户原因
            log.warn("登录失败：账号已禁用 | username={}", dto.getUsername());
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        } catch (BadCredentialsException e) {
            // ⚠️ 「用户不存在」与「密码错误」都走这里，返回同一句提示。
            //    目的是防止用户名枚举（见 UserDetailsServiceImpl 的说明）。
            log.warn("登录失败：凭据错误 | username={} | ip={}", dto.getUsername(), clientIp);
            throw new BizException(ResultCode.LOGIN_FAILED);
        } catch (AuthenticationException e) {
            log.warn("登录失败：{} | username={}", e.getClass().getSimpleName(), dto.getUsername());
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();

        // 记录登录审计信息；失败也不影响登录
        sysUserService.updateLoginInfo(loginUser.getUserId(), clientIp);

        log.info("登录成功 | userId={} | username={} | ip={}", loginUser.getUserId(), loginUser.getUsername(), clientIp);
        return tokenService.createTokenPair(loginUser);
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        // parse 内部已经做了签名与有效期校验
        Claims claims = tokenService.parse(refreshToken);

        // 必须校验类型：否则用户可以拿一个 accessToken 来换新令牌，
        // 等于把 30 分钟的有效期无限延长，短效设计就白做了。
        if (!TokenService.TYPE_REFRESH.equals(tokenService.getTokenType(claims))) {
            log.warn("刷新失败：令牌类型不是 refresh");
            throw new BizException(ResultCode.TOKEN_INVALID);
        }

        Long userId = tokenService.getUserId(claims);
        String jti = tokenService.getJti(claims);

        // 这一步是「可主动吊销」的落点：登出后 jti 已从 Redis 删除，这里就会失败
        if (!tokenService.isRefreshTokenActive(TokenService.SCOPE_ADMIN, userId, jti)) {
            log.warn("刷新失败：refreshToken 已失效（可能已登出或被轮换） | userId={}", userId);
            throw new BizException(ResultCode.TOKEN_EXPIRED);
        }

        // 轮换：旧的立刻作废，防止同一个 refreshToken 被重复使用
        tokenService.removeRefreshToken(TokenService.SCOPE_ADMIN, userId, jti);

        // 重新加载用户，保证权限变更能立刻生效（而不是沿用旧 token 里的快照）
        SysUser user = sysUserService.getRequiredById(userId);
        LoginUser loginUser = new LoginUser(user,
                sysPermissionService.getRoleCodes(userId),
                sysPermissionService.getPerms(userId));

        return tokenService.createTokenPair(loginUser);
    }

    @Override
    public void logout(String accessToken) {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();

        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Claims claims = tokenService.parse(accessToken);
                tokenService.blacklistAccessToken(claims);
            } catch (BizException e) {
                // token 已过期或非法：本来就用不了了，登出照样成功
                log.debug("登出时令牌已失效，忽略 | reason={}", e.getMessage());
            }
        }

        if (loginUser != null) {
            // 一次性吊销该用户在所有设备上的 refreshToken
            tokenService.removeAllRefreshTokens(TokenService.SCOPE_ADMIN, loginUser.getUserId());
            log.info("登出成功 | userId={} | username={}", loginUser.getUserId(), loginUser.getUsername());
        }
    }

    @Override
    public AdminInfoVO getCurrentUserInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return AdminInfoVO.builder()
                .userId(loginUser.getUserId())
                .username(loginUser.getUsername())
                .nickname(loginUser.getNickname())
                .avatar(loginUser.getAvatar())
                // 手机号是敏感信息，出口处统一脱敏
                .phone(MaskUtils.phone(loginUser.getPhone()))
                .roles(loginUser.getRoles())
                .perms(loginUser.getPerms())
                .build();
    }

    @Override
    public List<MenuTreeVO> getCurrentUserMenuTree() {
        return sysPermissionService.getMenuTree(SecurityUtils.getUserId());
    }
}
