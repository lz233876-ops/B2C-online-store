package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 登录成功返回的令牌对。
 * <p>
 * <b>双 Token 设计：</b>
 * <ul>
 *   <li>{@code accessToken} —— 短效（默认 30 分钟），无状态验签，每次请求都带；</li>
 *   <li>{@code refreshToken} —— 长效（默认 7 天），存在 Redis 里<b>可主动吊销</b>，
 *       只用于换新的 accessToken，不参与业务接口鉴权。</li>
 * </ul>
 * 为什么要两个：JWT 最大的缺点是<b>签发后无法主动失效</b>。如果只用一个长期 JWT，
 * 用户登出或被盗号后，那个 token 在过期前一直有效。拆成两个后：
 * access 短效把「失效延迟」压到 30 分钟内；refresh 存 Redis，登出即删，
 * 于是登出能立刻生效。
 */
@Data
@Builder
@Schema(description = "登录令牌")
public class LoginVO {

    @Schema(description = "访问令牌，放在请求头 Authorization: Bearer {accessToken}")
    private String accessToken;

    @Schema(description = "刷新令牌，仅用于调用 /api/admin/auth/refresh")
    private String refreshToken;

    @Schema(description = "令牌类型，固定为 Bearer")
    private String tokenType;

    @Schema(description = "accessToken 有效期（秒）")
    private Long expiresIn;
}
