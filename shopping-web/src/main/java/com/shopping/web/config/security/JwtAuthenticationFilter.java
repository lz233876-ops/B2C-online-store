package com.shopping.web.config.security;

import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.service.auth.LoginUserService;
import com.shopping.service.auth.TokenService;
import com.shopping.service.auth.config.JwtProperties;
import com.shopping.service.auth.security.LoginUser;
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
 * JWT 认证过滤器：把请求头里的 token 还原成 {@code SecurityContext} 里的登录态。
 * <p>
 * <b>在过滤器链中的位置</b>：插在 {@code UsernamePasswordAuthenticationFilter} 之前。
 * 位置很关键——必须早于 {@code ExceptionTranslationFilter} 和
 * {@code AuthorizationFilter}，否则等到鉴权时 {@code SecurityContext} 还是空的，
 * 所有受保护接口都会 401。
 * <p>
 * <b>本过滤器刻意不返回任何响应</b>：令牌无效时只清空上下文并记下错误码，
 * 让后面的 {@code ExceptionTranslationFilter} → {@link RestAuthenticationEntryPoint}
 * 统一产出 401。这样「401 长什么样」在整个系统里只有一处定义。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/admin/";

    private final TokenService tokenService;
    private final LoginUserService loginUserService;
    private final JwtProperties jwtProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        // 已经有登录态就跳过（例如同一次请求内被转发，避免重复解析）
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, token);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * <b>只处理后台路径</b>。
     * <p>
     * ⚠️ 这个方法不是优化，而是<b>安全边界</b>。第一版忘了加它，结果：
     * 一个<b>后台令牌</b>打到 {@code /api/app/**} 时，本过滤器照样把 {@code LoginUser}
     * 塞进 {@code SecurityContext}，于是 {@code .requestMatchers("/api/app/**").authenticated()}
     * 判定「已认证」→ 请求放行。虽然业务代码里取会员会拿到 null，
     * 但「认证通过」这件事本身已经是错误的——C 端接口不该接受后台身份。
     * <p>
     * 加了这个之后，两个过滤器各自只碰自己那一端的路径，
     * 「用后台令牌调 C 端接口」会在鉴权阶段直接 401。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            Claims claims = tokenService.parse(token);

            // 类型校验：refreshToken 不能用来访问业务接口，
            // 否则等于把「长效凭证」暴露在每一次请求里，短效 access 就失去意义了。
            if (!TokenService.TYPE_ACCESS.equals(tokenService.getTokenType(claims))) {
                log.warn("令牌类型不是 access，拒绝认证 | uri={}", request.getRequestURI());
                request.setAttribute(SecurityConstants.AUTH_ERROR_CODE_ATTR, ResultCode.TOKEN_INVALID.getCode());
                return;
            }

            // scope 校验（第二道防线）：即使路径匹配逻辑将来被改错，
            // 这里也不会把一个 C 端会员令牌当成后台身份。
            if (!TokenService.SCOPE_ADMIN.equals(tokenService.getUserScope(claims))) {
                log.warn("后台接口收到非 ADMIN scope 的令牌，拒绝认证 | uri={} | scope={}",
                        request.getRequestURI(), tokenService.getUserScope(claims));
                request.setAttribute(SecurityConstants.AUTH_ERROR_CODE_ATTR, ResultCode.TOKEN_INVALID.getCode());
                return;
            }

            // 黑名单校验：登出后 accessToken 虽然签名仍有效，但必须立刻作废。
            // 代价是每次请求多一次 Redis 查询 —— 这是「可主动失效」必须付的成本。
            if (tokenService.isAccessTokenBlacklisted(tokenService.getJti(claims))) {
                log.warn("令牌已在黑名单中（已登出） | uri={}", request.getRequestURI());
                request.setAttribute(SecurityConstants.AUTH_ERROR_CODE_ATTR, ResultCode.TOKEN_EXPIRED.getCode());
                return;
            }

            LoginUser loginUser = loginUserService.loadByUserId(tokenService.getUserId(claims));

            // 账号在签发令牌之后被禁用 —— 这里再挡一次，不必等令牌过期
            if (!loginUser.isEnabled()) {
                log.warn("账号已禁用，拒绝认证 | userId={}", loginUser.getUserId());
                request.setAttribute(SecurityConstants.AUTH_ERROR_CODE_ATTR, ResultCode.ACCOUNT_DISABLED.getCode());
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (BizException e) {
            // 过期 / 非法：记下错误码，交给 EntryPoint 统一输出
            SecurityContextHolder.clearContext();
            request.setAttribute(SecurityConstants.AUTH_ERROR_CODE_ATTR, e.getCode());
        }
    }

    /**
     * 从请求头取 token。
     * <p>
     * 兼容两种写法：标准的 {@code Authorization: Bearer xxx}，以及直接贴 token。
     * 后者是为了方便用 Postman / curl 手工调试——<b>安全性不受影响</b>，
     * 因为真正把关的是签名校验，不是前缀格式。
     */
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
