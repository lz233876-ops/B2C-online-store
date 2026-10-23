package com.shopping.web.controller.app;

import com.shopping.common.result.Result;
import com.shopping.model.dto.AppLoginDTO;
import com.shopping.model.dto.AppRegisterDTO;
import com.shopping.model.dto.RefreshTokenDTO;
import com.shopping.model.vo.AppUserInfoVO;
import com.shopping.model.vo.LoginVO;
import com.shopping.service.auth.AppAuthService;
import com.shopping.service.auth.TokenService;
import com.shopping.service.auth.config.JwtProperties;
import com.shopping.web.util.IpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端会员认证接口。
 * <p>
 * 与后台的 {@code /api/admin/auth/**} 是<b>两套完全独立的入口</b>：
 * 不同的表、不同的认证管理器、不同的令牌 scope。
 * 拿会员令牌调后台接口会被后台过滤器拒（scope 不是 ADMIN），反之亦然。
 */
@Tag(name = "20-C端 · 认证", description = "会员注册、登录、刷新令牌、登出、当前会员")
@RestController
@RequestMapping("/api/app/auth")
@RequiredArgsConstructor
public class AppAuthController {

    private final AppAuthService appAuthService;
    private final JwtProperties jwtProperties;

    @Operation(summary = "会员注册", description = "手机号 + 密码；注册成功后不自动登录")
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody AppRegisterDTO dto, HttpServletRequest request) {
        return Result.success("注册成功", appAuthService.register(dto, IpUtils.getClientIp(request)));
    }

    @Operation(summary = "会员登录", description = "手机号 + 密码，返回 accessToken + refreshToken（scope=APP）")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody AppLoginDTO dto, HttpServletRequest request) {
        return Result.success("登录成功", appAuthService.login(dto, IpUtils.getClientIp(request)));
    }

    @Operation(summary = "刷新令牌", description = "旧的 refreshToken 立即失效（轮换）；仅接受 scope=APP 的令牌")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return Result.success(appAuthService.refresh(dto.getRefreshToken()));
    }

    @Operation(summary = "会员登出", description = "拉黑当前 accessToken 并吊销该会员全部 refreshToken")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        appAuthService.logout(stripPrefix(authorization));
        return Result.<Void>success("已登出", null);
    }

    @Operation(summary = "当前会员信息")
    @GetMapping("/me")
    public Result<AppUserInfoVO> me() {
        return Result.success(appAuthService.getCurrentUserInfo());
    }

    private String stripPrefix(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String prefix = jwtProperties.getTokenPrefix();
        if (prefix != null && !prefix.isBlank() && authorization.startsWith(prefix)) {
            return authorization.substring(prefix.length()).trim();
        }
        return authorization.trim();
    }
}
