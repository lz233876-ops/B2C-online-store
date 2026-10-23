package com.shopping.web.config.security;

import com.shopping.common.result.Result;
import com.shopping.common.result.ResultCode;
import com.shopping.service.auth.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 无权限处理器：已登录但缺少所需权限时调用，返回 HTTP 403。
 * <p>
 * 会走到这里的是 {@code @PreAuthorize} 校验失败。注意一个<b>很容易踩的坑</b>：
 * {@code @PreAuthorize} 抛出的 {@code AccessDeniedException} 是在 Controller
 * 方法调用时抛的，会先经过 {@code @RestControllerAdvice} 的异常处理链。
 * 如果全局异常处理器有一个宽泛的 {@code @ExceptionHandler(Exception.class)}，
 * 它会把 403 吞掉并返回 500。所以 {@code GlobalExceptionHandler} 里必须
 * <b>把 AccessDeniedException 原样抛出去</b>，交给本处理器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityResponseWriter responseWriter;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        var loginUser = SecurityUtils.getLoginUserOrNull();
        log.warn("权限不足，拒绝访问 | uri={} | userId={} | authorities={}",
                request.getRequestURI(),
                loginUser == null ? null : loginUser.getUserId(),
                loginUser == null ? null : loginUser.authoritySummary());
        responseWriter.write(response, HttpServletResponse.SC_FORBIDDEN, Result.failure(ResultCode.FORBIDDEN));
    }
}
