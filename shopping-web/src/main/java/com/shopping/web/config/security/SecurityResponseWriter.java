package com.shopping.web.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 把 {@link Result} 写成 JSON 响应。
 * <p>
 * 抽出来是因为 401（未认证）与 403（无权限）两个处理器需要完全一样的输出格式。
 * 认证/授权失败发生在 <b>Filter 层</b>，此时还没进 DispatcherServlet，
 * 所以 {@code @RestControllerAdvice} 帮不上忙——必须自己序列化。
 * <p>
 * 这也是为什么 {@code Content-Type} 要显式指定 {@code charset=UTF-8}：
 * 系统默认编码是 GBK，不写清楚中文提示会变成乱码。
 */
@Component
@RequiredArgsConstructor
public class SecurityResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, int httpStatus, Result<?> body) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
