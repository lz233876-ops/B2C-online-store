package com.shopping.web.controller.app;

import com.shopping.common.constant.CommonConstants;
import com.shopping.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查（业务侧）。
 * <p>
 * 与 Actuator 的 {@code /actuator/health} 区别：
 * <ul>
 *   <li>{@code /actuator/health} 用于运维探活，返回的是组件状态（DB、Redis 是否连通）；</li>
 *   <li>本接口用于<b>联调</b>，用来验证「统一响应体 + 全局异常 + 文档 + traceId」这条链路是否通了。</li>
 * </ul>
 */
@Tag(name = "00-健康检查", description = "验证服务是否正常启动")
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class HealthController {

    private final Environment environment;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Operation(summary = "服务探活", description = "返回服务名、端口、当前时间，用于确认服务已启动")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", environment.getProperty("spring.application.name", "shopping-mall"));
        data.put("activeProfiles", String.join(",", environment.getActiveProfiles()));
        data.put("port", environment.getProperty("local.server.port", "8080"));
        data.put("serverTime", LocalDateTime.now().format(FORMATTER));
        data.put("merchantId", CommonConstants.DEFAULT_MERCHANT_ID);
        return Result.success("服务正常", data);
    }
}
