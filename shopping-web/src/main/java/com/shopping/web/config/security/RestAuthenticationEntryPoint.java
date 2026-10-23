package com.shopping.web.config.security;

import com.shopping.common.result.Result;
import com.shopping.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证处理器：请求需要登录但没带（或带了无效）令牌时调用，返回 HTTP 401。
 * <p>
 * 这是本项目<b>唯一</b>会返回非 200 HTTP 状态码的地方。为什么这里破例：
 * 前端需要一个统一的、无需解析响应体就能识别的信号来触发「跳登录页 + 发起静默刷新」。
 * 如果这里也返回 200，前端就得先解析 JSON 的 code 字段，多一步且容易漏判。
 * <p>
 * 其余业务失败一律返回 HTTP 200 + 业务码，由前端按 {@code code} 分流。
 * 这条规则写在 {@code Result} 的注释里，保持前后端约定一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityResponseWriter responseWriter;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ResultCode code = resolveCode(request);
        log.debug("未认证访问 | uri={} | code={}", request.getRequestURI(), code.getCode());
        responseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, Result.failure(code));
    }

    /**
     * 区分「令牌过期」与「令牌非法」，因为前端处理方式不同：
     * 过期 → 静默用 refreshToken 换新的再重试；非法 → 直接跳登录页。
     */
    private ResultCode resolveCode(HttpServletRequest request) {
        Object attr = request.getAttribute(SecurityConstants.AUTH_ERROR_CODE_ATTR);
        if (attr instanceof Integer errorCode) {
            if (errorCode == ResultCode.TOKEN_EXPIRED.getCode()) {
                return ResultCode.TOKEN_EXPIRED;
            }
            if (errorCode == ResultCode.TOKEN_INVALID.getCode()) {
                return ResultCode.TOKEN_INVALID;
            }
            if (errorCode == ResultCode.ACCOUNT_DISABLED.getCode()) {
                return ResultCode.ACCOUNT_DISABLED;
            }
        }
        // 压根没带令牌
        return ResultCode.UNAUTHORIZED;
    }
}
