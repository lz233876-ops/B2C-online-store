package com.shopping.service.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置。
 * <p>
 * {@code secret} 必须通过环境变量或 {@code application-dev.yml} 注入，
 * <b>不允许写死在提交到 Git 的文件里</b>——密钥一旦泄漏，任何人都能伪造任意用户的 token。
 */
@Data
@Component
@ConfigurationProperties(prefix = "shopping.jwt")
public class JwtProperties {

    /**
     * 签名密钥（HS256）。
     * <p>
     * ⚠️ HS256 要求密钥长度 <b>至少 256 位（32 字节）</b>。
     * 短于 32 字节时 jjwt 会直接抛 {@code WeakKeyException}——
     * 这是它刻意做的保护，因为短密钥可以暴力破解。
     */
    private String secret;

    /** 签发者，会写进 token 的 iss 声明并在校验时比对，防止不同系统的 token 互相通用 */
    private String issuer = "shopping-mall";

    /** accessToken 有效期（分钟）。短效是为了把「无法主动失效」的窗口压小 */
    private long accessTokenExpireMinutes = 30;

    /** refreshToken 有效期（天） */
    private long refreshTokenExpireDays = 7;

    /** 取 token 的请求头名 */
    private String header = "Authorization";

    /** token 前缀，注意 Bearer 后面有一个空格 */
    private String tokenPrefix = "Bearer ";
}
