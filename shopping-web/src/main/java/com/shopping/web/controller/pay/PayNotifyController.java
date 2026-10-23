package com.shopping.web.controller.pay;

import com.shopping.service.pay.PayNotifyResult;
import com.shopping.service.pay.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付渠道回调入口。
 * <p>
 * <b>⚠️ 本接口的响应体是纯文本 {@code "success"} / {@code "failure"}，
 * 不是项目统一的 {@code Result} 结构。这是刻意的：</b>
 * <b>回调的协议由渠道定义，不由我们定义。</b>渠道只认它自己的约定
 * （支付宝认 {@code "success"}），给它包一层 JSON 会被当成失败并无限重试。
 * 「统一响应格式」是内部 API 的规范，对外回调必须让位于对方的协议。
 * <p>
 * 本接口不需要登录态：调用方是渠道服务器，它没有我们的用户令牌。
 * 安全性由<b>验签</b>保证，而不是由身份认证保证 —— 这也是它必须公开的原因。
 */
@Slf4j
@Tag(name = "90-支付回调（渠道调用，无统一 Result 包装）")
@RestController
@RequestMapping("/api/pay/notify")
@RequiredArgsConstructor
public class PayNotifyController {

    /** 渠道约定的成功回执字面量 */
    private static final String RESP_SUCCESS = "success";
    /** 渠道约定的失败回执字面量：渠道看到它才会重试 */
    private static final String RESP_FAILURE = "failure";

    private final PaymentService paymentService;

    /**
     * 接收支付结果通知。
     * <p>参数用 {@code @RequestParam Map} 全量接收而不是定义 DTO：
     * 渠道会附加我们没声明过的字段，而<b>这些字段同样参与验签</b>——
     * 用 DTO 只接自己认识的字段会导致验签永远失败。这是接入回调时的一个经典坑。
     */
    @Operation(summary = "支付结果回调", description = "渠道服务器调用；返回纯文本 success/failure")
    @PostMapping(value = "/{channel}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(@PathVariable int channel,
                         @RequestParam Map<String, String> params,
                         HttpServletRequest request) {
        String rawBody = buildRawBody(params);
        String headers = buildHeaders(request);
        try {
            PayNotifyResult result = paymentService.handleNotify(channel, params, rawBody, headers);
            // 无论是首次处理成功还是「重复通知已忽略」，都回 success ——
            // 让渠道停止重试是我们的目的，而重复通知本身不是错误
            return result.success() ? RESP_SUCCESS : RESP_FAILURE;
        } catch (Exception e) {
            // 兜底：任何意料之外的异常都必须回 failure 让渠道重试，
            // 绝不能让它变成 500 —— 有些渠道把 5xx 视为「对端故障」并改变重试策略
            log.error("支付回调出现未捕获异常 | channel={}", channel, e);
            return RESP_FAILURE;
        }
    }

    private static String buildRawBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static String buildHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers.toString();
    }
}
