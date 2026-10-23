package com.shopping.web.filter;

import com.shopping.common.constant.CommonConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器。
 * <p>
 * 为每个请求生成一个 {@code traceId}，放进 SLF4J 的 {@link MDC}，
 * 这样日志格式里的 {@code %X{traceId}} 就会自动带上它，
 * 排查问题时可以按 traceId 把一次请求涉及的所有日志串起来。
 * <p>
 * 两个细节：
 * <ol>
 *   <li>如果上游（网关/前端）已经带了 {@code X-Trace-Id}，就<b>复用</b>而不是重新生成，
 *       否则跨服务/跨端调用会断链；</li>
 *   <li>必须在 {@code finally} 里 {@code MDC.clear()}。Tomcat 的工作线程是复用，
 *       不清理会导致 traceId 串到下一个请求上——这是 MDC 最经典的坑。</li>
 * </ol>
 * <p>
 * 优先级设为最高，保证它比日志、认证等所有过滤器都早执行。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(CommonConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(CommonConstants.TRACE_ID, traceId);
        response.setHeader(CommonConstants.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程会被 Tomcat 复用，不清理会污染下一个请求
            MDC.remove(CommonConstants.TRACE_ID);
        }
    }
}
