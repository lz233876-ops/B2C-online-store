package com.shopping.web.controller.admin;

import com.shopping.common.result.Result;
import com.shopping.model.dto.LoginDTO;
import com.shopping.model.dto.RefreshTokenDTO;
import com.shopping.model.vo.AdminInfoVO;
import com.shopping.model.vo.LoginVO;
import com.shopping.model.vo.MenuTreeVO;
import com.shopping.service.auth.AuthService;
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

import java.util.List;

/**
 * 后台认证接口。
 * <p>
 * 关于「为什么 refreshToken 放请求体而不是请求头」：
 * 请求头 {@code Authorization} 只承载 accessToken，职责单一。
 * 把 refreshToken 也塞进去会让过滤器难以区分该用哪个；
 * 而 refresh 是一个明确的、只调用一次的动作，放请求体语义更清楚。
 */
@Tag(name = "01-认证授权", description = "后台登录、刷新令牌、登出、当前用户")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @Operation(summary = "登录", description = "账号密码登录，成功返回 accessToken + refreshToken")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        // IP 由 Controller 取好再传给 Service —— Service 层不碰 HttpServletRequest，
        // 否则 service 模块会依赖 web 模块（Maven Enforcer 会直接让构建失败）
        return Result.success("登录成功", authService.login(dto, IpUtils.getClientIp(request)));
    }

    @Operation(summary = "刷新令牌", description = "用 refreshToken 换取新的令牌对；旧 refreshToken 立即失效（轮换）")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return Result.success(authService.refresh(dto.getRefreshToken()));
    }

    @Operation(summary = "登出", description = "吊销当前 accessToken 与该用户全部 refreshToken")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(stripPrefix(authorization));
        return Result.<Void>success("已登出", null);
    }

    @Operation(summary = "当前登录用户信息", description = "含角色编码与权限标识，供前端做按钮级控制")
    @GetMapping("/me")
    public Result<AdminInfoVO> me() {
        return Result.success(authService.getCurrentUserInfo());
    }

    @Operation(summary = "当前用户动态路由", description = "返回该用户有权访问的菜单树，供前端注册路由")
    @GetMapping("/menus")
    public Result<List<MenuTreeVO>> menus() {
        return Result.success(authService.getCurrentUserMenuTree());
    }

    /**
     * 去掉 {@code Bearer } 前缀。
     * 与 {@code JwtAuthenticationFilter.resolveToken} 保持同样的宽容策略：
     * 带前缀、不带前缀都能用，方便用 curl / Postman 手工调试。
     */
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
