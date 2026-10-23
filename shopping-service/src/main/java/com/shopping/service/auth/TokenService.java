package com.shopping.service.auth;

import com.shopping.model.vo.LoginVO;
import com.shopping.service.auth.security.LoginUser;
import io.jsonwebtoken.Claims;

/**
 * 令牌服务：负责 JWT 的签发、解析、吊销。
 * <p>
 * 单独抽出来而不塞进 {@code AuthService}，是因为「令牌的生命周期管理」
 * 与「登录业务」是两件独立变化的事：将来换签名算法（HS256 → RS256）、
 * 换存储（Redis → 数据库），只需要动这一层。
 * <p>
 * <b>{@code userScope} 是本项目认证设计的关键：</b>
 * 令牌里带 {@code ADMIN} / {@code APP} 声明，
 * 两个 JWT 过滤器各自只认自己那一种 scope。
 * 于是「用会员 token 访问后台接口」在<b>过滤器阶段</b>就被拒——
 * 不依赖任何 {@code @PreAuthorize} 写对，是结构性保证。
 */
public interface TokenService {

    /** 令牌类型：访问令牌 */
    String TYPE_ACCESS = "access";
    /** 令牌类型：刷新令牌 */
    String TYPE_REFRESH = "refresh";
    /** 请求头里的前缀 */
    String BEARER = "Bearer";

    /** 用户域：后台用户（{@code sys_user}） */
    String SCOPE_ADMIN = "ADMIN";
    /** 用户域：C 端会员（{@code ums_user}） */
    String SCOPE_APP = "APP";

    /**
     * 为<b>后台用户</b>签发一对令牌（access + refresh），并把 refresh 的 jti 记入 Redis。
     * <p>等价于 {@code createTokenPair(userId, username, SCOPE_ADMIN)}。
     */
    LoginVO createTokenPair(LoginUser loginUser);

    /**
     * 通用签发：为任意用户域的账号签发令牌对。
     *
     * @param userId    账号 ID
     * @param username  账号标识（后台是 username，C 端是手机号）
     * @param userScope {@link #SCOPE_ADMIN} 或 {@link #SCOPE_APP}
     */
    LoginVO createTokenPair(Long userId, String username, String userScope);

    /**
     * 校验签名与有效期并解析出声明。
     *
     * @throws com.shopping.common.exception.BizException 过期抛 {@code TOKEN_EXPIRED}，
     *         签名错误/格式非法抛 {@code TOKEN_INVALID}
     */
    Claims parse(String token);

    /** 取用户 ID（token 的 sub） */
    Long getUserId(Claims claims);

    /** 取令牌唯一标识（token 的 jti），吊销与黑名单都以它为准 */
    String getJti(Claims claims);

    /** 取令牌类型：access / refresh */
    String getTokenType(Claims claims);

    /** 取用户域：ADMIN / APP。缺失时返回 {@code null}（老令牌或伪造令牌） */
    String getUserScope(Claims claims);

    /** refreshToken 是否仍然有效（即 jti 还在 Redis 里，未被登出或轮换掉） */
    boolean isRefreshTokenActive(String userScope, Long userId, String jti);

    /** 吊销单个 refreshToken（刷新时轮换用） */
    void removeRefreshToken(String userScope, Long userId, String jti);

    /** 吊销某用户的全部 refreshToken（改密码、强制下线用） */
    void removeAllRefreshTokens(String userScope, Long userId);

    /**
     * 把 accessToken 加入黑名单。
     * <p>TTL 取该 token 的<b>剩余有效期</b>，过期后自动清理，避免黑名单无限膨胀。
     */
    void blacklistAccessToken(Claims claims);

    /** accessToken 是否已被拉黑 */
    boolean isAccessTokenBlacklisted(String jti);
}
