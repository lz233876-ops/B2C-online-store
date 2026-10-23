package com.shopping.service.auth.impl;

import com.shopping.common.constant.RedisKeys;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.model.vo.LoginVO;
import com.shopping.service.auth.TokenService;
import com.shopping.service.auth.config.JwtProperties;
import com.shopping.service.auth.security.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * 令牌服务实现。
 * <p>
 * <b>令牌结构设计：</b>
 * <pre>
 * accessToken  声明: sub=userId, jti=随机串, iss, iat, exp, type=access,  username
 * refreshToken 声明: sub=userId, jti=随机串, iss, iat, exp, type=refresh, username
 * </pre>
 * <b>刻意不把 roles / perms 放进 token</b>：那样 token 会变得很大（38 个权限字符串
 * 会让请求头膨胀到几 KB），而且<b>权限变更后必须等 token 过期才生效</b>，
 * 无法做到「管理员改了权限立刻生效」。所以权限每次请求从库里读（见 UserDetailsServiceImpl）。
 * <p>
 * <b>为什么用 {@link StringRedisTemplate} 而不是项目里配的
 * {@code RedisTemplate<String,Object>}：</b>
 * 这里存的是纯基础设施数据（jti 字符串），不需要对象序列化。
 * 用 String 模板可以避免 JSON 序列化器给值包一层引号，
 * 直接 {@code redis-cli} 看到的也是干净的值，便于排查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_USERNAME = "username";
    /** 用户域声明：ADMIN / APP。两个过滤器各自只认自己那一种 */
    private static final String CLAIM_USER_SCOPE = "userScope";
    private static final String BLACKLIST_VALUE = "1";

    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;

    private SecretKey secretKey;

    /**
     * 启动时校验密钥强度。
     * <p>
     * 必须<b>提前失败</b>：如果密钥是空或太短，jjwt 会在第一次登录时才抛
     * {@code WeakKeyException}，那时你已经以为是业务 bug 了。
     * 在 {@code @PostConstruct} 里直接崩掉，错误信息还能自己写清楚。
     */
    @PostConstruct
    void initSecretKey() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "未配置 JWT 密钥。请在 application-dev.yml 里设置 shopping.jwt.secret，"
                            + "或设置环境变量 SHOPPING_JWT_SECRET。该值绝不能提交到 Git。");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT 密钥太短（当前 " + keyBytes.length + " 字节）。HS256 要求至少 32 字节（256 位），"
                            + "否则可被暴力破解。请换一个更长的随机字符串。");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT 初始化完成 | issuer={} | accessToken={}min | refreshToken={}d",
                jwtProperties.getIssuer(),
                jwtProperties.getAccessTokenExpireMinutes(),
                jwtProperties.getRefreshTokenExpireDays());
    }

    @Override
    public LoginVO createTokenPair(LoginUser loginUser) {
        return createTokenPair(loginUser.getUserId(), loginUser.getUsername(), SCOPE_ADMIN);
    }

    @Override
    public LoginVO createTokenPair(Long userId, String username, String userScope) {
        long now = System.currentTimeMillis();
        long accessTtlMillis = Duration.ofMinutes(jwtProperties.getAccessTokenExpireMinutes()).toMillis();
        long refreshTtlMillis = Duration.ofDays(jwtProperties.getRefreshTokenExpireDays()).toMillis();

        String accessJti = newJti();
        String refreshJti = newJti();

        String accessToken = buildToken(userId, username, userScope, TYPE_ACCESS, accessJti, now, accessTtlMillis);
        String refreshToken = buildToken(userId, username, userScope, TYPE_REFRESH, refreshJti, now, refreshTtlMillis);

        // refreshToken 的 jti 落 Redis，这是它能被「主动吊销」的关键。
        // 用 Hash 存，field = jti，于是「强制该用户所有设备下线」= DEL 这一个 key。
        // key 里带 userScope：后台与 C 端的会话互不影响。
        String refreshKey = RedisKeys.authRefresh(userScope, userId);
        stringRedisTemplate.opsForHash().put(refreshKey, refreshJti, String.valueOf(now));
        stringRedisTemplate.expire(refreshKey, Duration.ofMillis(refreshTtlMillis));

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(BEARER)
                .expiresIn(accessTtlMillis / 1000)
                .build();
    }

    @Override
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    // 校验签发者，防止别的系统用同样的密钥签发的 token 被我们接受
                    .requireIssuer(jwtProperties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // 过期与非法要分开：前端遇到「过期」应静默去刷新，
            // 遇到「非法」则说明 token 被篡改，应直接跳登录页。
            throw new BizException(ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("令牌解析失败 | reason={}", e.getMessage());
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
    }

    @Override
    public Long getUserId(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
    }

    @Override
    public String getJti(Claims claims) {
        return claims.getId();
    }

    @Override
    public String getTokenType(Claims claims) {
        return claims.get(CLAIM_TYPE, String.class);
    }

    @Override
    public String getUserScope(Claims claims) {
        return claims.get(CLAIM_USER_SCOPE, String.class);
    }

    @Override
    public boolean isRefreshTokenActive(String userScope, Long userId, String jti) {
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForHash().hasKey(RedisKeys.authRefresh(userScope, userId), jti));
    }

    @Override
    public void removeRefreshToken(String userScope, Long userId, String jti) {
        if (jti == null) {
            return;
        }
        stringRedisTemplate.opsForHash().delete(RedisKeys.authRefresh(userScope, userId), jti);
    }

    @Override
    public void removeAllRefreshTokens(String userScope, Long userId) {
        stringRedisTemplate.delete(RedisKeys.authRefresh(userScope, userId));
    }

    @Override
    public void blacklistAccessToken(Claims claims) {
        if (claims == null || claims.getId() == null || claims.getExpiration() == null) {
            return;
        }
        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            // 已经过期了，本来就会失效，不需要占黑名单空间
            return;
        }
        stringRedisTemplate.opsForValue().set(
                RedisKeys.authBlacklist(claims.getId()),
                BLACKLIST_VALUE,
                Duration.ofMillis(remainingMillis));
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisKeys.authBlacklist(jti)));
    }

    private String buildToken(Long userId, String username, String userScope,
                              String type, String jti, long now, long ttlMillis) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(jti)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_USERNAME, username)
                // userScope 让两个过滤器能各认各的令牌，是「两条独立认证链」的落地方式
                .claim(CLAIM_USER_SCOPE, userScope)
                .signWith(secretKey)
                .compact();
    }

    private String newJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
