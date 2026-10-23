package com.shopping.web.config.security;

import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.service.auth.AppLoginUserService;
import com.shopping.service.auth.TokenService;
import com.shopping.service.auth.config.JwtProperties;
import com.shopping.service.auth.security.AppLoginUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * C 端会员的 JWT 认证过滤器。
 * <p>
 * <b>与后台过滤器的三条关键差异：</b>
 * <ol>
 *   <li>{@link #shouldNotFilter} 只处理 {@code /api/app/**}，
 *       后台请求连解析都不做（省掉无谓的 Redis 查询与验签开销）；</li>
 *   <li>要求令牌的 {@code userScope == APP}，<b>后台令牌在这里直接无效</b>；</li>
 *   <li>装载的是 {@link AppLoginUser}，与后台主体是不同类型。</li>
 * </ol>
 * <p>
 * 这三点合起来，构成「用会员令牌调后台接口 / 用后台令牌调会员接口」都失败的<b>结构性保证</b>——
 * 不依赖任何一处 {@code @PreAuthorize} 或 if 判断写对。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String APP_PATH_PREFIX = "/api/app/";

    private final TokenService tokenService;
    private final AppLoginUserService appLoginUserService;
    private final JwtProperties jwtProperties;

    /** 只处理 C 端路径；后台与静态资源一律跳过 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(APP_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, token);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            Claims claims = tokenService.parse(token);

            if (!TokenService.TYPE_ACCESS.equals(tokenService.getTokenType(claims))) {
                log.warn("C 端令牌类型不是 access，拒绝认证 | uri={}", request.getRequestURI());
                request.setAttribute(SecurityConstants.AUTH_ERROR_CODE_ATTR, ResultCode.TOKEN_INVALID.getCode());
                return;
            }
            // ⚠️ scope 校验：这是「两条认证链隔离」在运行时的落点
            if (!TokenService.SCOPE_APP.equals(tokenService.getUserScope(claims))) {
                log.warn("C 端接口收到非 APP scope 的令牌，拒绝认证 | uri={} | scope={}",
                        request.getRequestURI(), tokenService.getUserScope(claims));
                request.setAttribute(SecurityConstants.AUTH_ERROR_CODE_ATTR, ResultCode.TOKEN_INVALID.getCode());
                return;
            }
            if (tokenService.isAccessTokenBlacklisted(tokenService.getJti(claims))) {
                log.warn("C 端令牌已在黑名单中（已登出） | uri={}", request.getRequestURI());
                request.setAttribute(SecurityConstants.AUTH_ERROR_CODE_ATTR, ResultCode.TOKEN_EXPIRED.getCode());
                return;
            }

            AppLoginUser appLoginUser = appLoginUserService.loadByUserId(tokenService.getUserId(claims));
            if (!appLoginUser.isEnabled()) {
                log.warn("会员账号已禁用，拒绝认证 | userId={}", appLoginUser.getUserId());
                request.setAttribute(SecurityConstants.AUTH_ERROR_CODE_ATTR, ResultCode.MEMBER_DISABLED.getCode());
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(appLoginUser, null, appLoginUser.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BizException e) {
            SecurityContextHolder.clearContext();
            request.setAttribute(SecurityConstants.AUTH_ERROR_CODE_ATTR, e.getCode());
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(jwtProperties.getHeader());
        if (header == null || header.isBlank()) {
            return null;
        }
        String prefix = jwtProperties.getTokenPrefix();
        if (prefix != null && !prefix.isBlank() && header.startsWith(prefix)) {
            return header.substring(prefix.length()).trim();
        }
        return header.trim();
    }
}
