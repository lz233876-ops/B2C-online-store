package com.shopping.web.controller.pay;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.Result;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.PayPaymentMapper;
import com.shopping.model.entity.PayPayment;
import com.shopping.model.vo.MockNotifyVO;
import com.shopping.service.pay.MockPayGateway;
import com.shopping.service.pay.PayNotifyResult;
import com.shopping.service.pay.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>模拟收银台 / 模拟渠道</b>。
 * <p>
 * 它扮演的是<b>渠道那一侧</b>：用户在这里「完成付款」，然后它按渠道的规则
 * 组装通知报文、计算签名、投递给我们真实的回调处理逻辑。
 * <p>
 * <b>为什么它必须公开且不需要登录：</b>因为它模拟的是外部系统，
 * 外部系统当然没有我们用户的令牌。真实接入时这个 Controller 会被删掉，
 * 换成真正的渠道服务器。
 * <p>
 * <b>接口拆成「生成报文」与「投递报文」两个，是刻意的</b>：
 * 真实渠道也是先组报文、再按自己的节奏（含重试）投递。拆开之后：
 * <ul>
 *   <li>{@code build-notify} —— 只生成带签名的报文，<b>不投递</b>。
 *       验收脚本拿它来<b>篡改</b>（改金额、改签名）后自行投递，从而验证验签与金额校验；</li>
 *   <li>{@code pay} —— 生成并立刻投递，模拟「用户付款成功」这一完整动作。</li>
 * </ul>
 * ⚠️ 早先把两者合成一个接口时踩过坑：脚本为了「拿到报文」而调了它，
 * 结果<b>支付真的发生了</b>，后面的篡改测试全部失去意义
 * （订单已经付掉了，篡改也无从验证）。<b>「读取」与「写入」绝不能共用一个入口。</b>
 */
@Slf4j
@Tag(name = "91-模拟支付渠道（演示用，接入真实渠道时删除）")
@RestController
@RequestMapping("/api/pay/mock-gateway")
@RequiredArgsConstructor
public class MockPayGatewayController {

    private static final DateTimeFormatter NOTIFY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    private static final String TRADE_CLOSED = "TRADE_CLOSED";

    private final PayPaymentMapper paymentMapper;
    private final MockPayGateway gateway;
    private final PaymentService paymentService;

    /**
     * 只生成渠道通知报文与签名，<b>不投递</b>。
     * <p>用于验收：篡改报文后再自行投递，验证验签与金额校验真的拦得住。
     */
    @Operation(summary = "生成渠道通知报文（不投递）",
            description = "返回带签名的通知参数；供验收脚本篡改后自行投递，以测试验签与金额校验")
    @PostMapping("/{paymentNo}/build-notify")
    public Result<MockNotifyVO> buildNotify(@PathVariable String paymentNo,
                                            @RequestParam(defaultValue = "true") boolean success,
                                            @RequestParam(required = false) Long overrideAmount,
                                            @RequestParam(required = false) String overrideNotifyId) {
        PayPayment payment = requirePayment(paymentNo);
        MockNotifyVO vo = build(payment, success, overrideAmount, overrideNotifyId);
        vo.setMessage("仅生成报文，未投递");
        return Result.success(vo);
    }

    /**
     * 在模拟收银台完成付款，生成并投递渠道通知。
     *
     * @param success          模拟支付成功还是渠道侧关闭
     * @param overrideAmount   用<b>另一个金额</b>发起通知（签名仍然正确）—— 专门用来验证金额校验
     * @param overrideNotifyId 指定 notify_id，便于脚本构造「换 id 重投」的场景
     */
    @Operation(summary = "模拟收银台付款（渠道侧动作）",
            description = "组装带签名的渠道通知并投递给真实回调处理逻辑；返回所用报文以便重放")
    @PostMapping("/{paymentNo}/pay")
    public Result<MockNotifyVO> pay(@PathVariable String paymentNo,
                                    @RequestParam(defaultValue = "true") boolean success,
                                    @RequestParam(required = false) Long overrideAmount,
                                    @RequestParam(required = false) String overrideNotifyId) {
        PayPayment payment = requirePayment(paymentNo);
        MockNotifyVO vo = build(payment, success, overrideAmount, overrideNotifyId);

        // 投递给真实回调逻辑：验签、幂等、金额校验、推进订单、库存转实扣，一个都不少
        PayNotifyResult result;
        try {
            result = paymentService.handleNotify(payment.getChannel(), vo.getParams(),
                    vo.getParams().toString(), "{mock-gateway: true}");
        } catch (Exception e) {
            log.error("模拟渠道投递通知时异常 | paymentNo={}", paymentNo, e);
            result = PayNotifyResult.fail("投递异常：" + e.getMessage());
        }

        vo.setSuccess(result.success());
        vo.setDuplicate(result.duplicate());
        vo.setNeedRefund(result.needRefund());
        vo.setMessage(result.message());
        vo.setNotifyResponse(result.success() ? "success" : "failure");
        log.info("模拟渠道已投递通知 | paymentNo={} | notifyId={} | 结果={}",
                paymentNo, vo.getNotifyId(), result.message());
        return Result.success("模拟渠道已投递通知", vo);
    }

    /* ---------------- 内部方法 ---------------- */

    private PayPayment requirePayment(String paymentNo) {
        PayPayment payment = paymentMapper.selectOne(Wrappers.<PayPayment>lambdaQuery()
                .eq(PayPayment::getPaymentNo, paymentNo));
        if (payment == null) {
            // 抛业务异常交给全局异常处理器，保持与其它接口一致的响应结构
            throw new BizException(ResultCode.PAYMENT_NOT_FOUND, "支付单不存在：" + paymentNo);
        }
        return payment;
    }

    /** 渠道侧组报文 + 算签名。字段命名刻意对标支付宝 */
    private MockNotifyVO build(PayPayment payment, boolean success,
                               Long overrideAmount, String overrideNotifyId) {
        String notifyId = (overrideNotifyId == null || overrideNotifyId.isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : overrideNotifyId;

        Map<String, String> params = new LinkedHashMap<>();
        params.put("notify_id", notifyId);
        params.put("notify_time", LocalDateTime.now().format(NOTIFY_TIME));
        params.put("channel", String.valueOf(payment.getChannel()));
        // out_trade_no 是渠道视角的「商户订单号」，这里就是我们的支付单号
        params.put("out_trade_no", payment.getPaymentNo());
        params.put("order_no", payment.getOrderNo());
        params.put("trade_no", "MOCKTRADE" + notifyId.substring(0, Math.min(12, notifyId.length())));
        params.put("trade_status", success ? TRADE_SUCCESS : TRADE_CLOSED);
        params.put("total_amount", String.valueOf(
                overrideAmount == null ? payment.getAmount() : overrideAmount));
        params.put("buyer_logon_id", "mock_buyer@example.com");

        String sign = gateway.sign(params);
        params.put("sign", sign);
        params.put("sign_type", "HMAC-SHA256");

        MockNotifyVO vo = new MockNotifyVO();
        vo.setNotifyId(notifyId);
        vo.setParams(params);
        vo.setSign(sign);
        return vo;
    }
}
