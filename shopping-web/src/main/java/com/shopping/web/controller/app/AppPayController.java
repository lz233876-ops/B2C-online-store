package com.shopping.web.controller.app;

import com.shopping.common.result.Result;
import com.shopping.model.dto.PayCreateDTO;
import com.shopping.model.vo.PaymentVO;
import com.shopping.service.pay.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端支付接口。
 * <p>
 * ⚠️ <b>真实接入支付宝/微信需要企业资质与商户号，本项目【不接入真实支付】。</b>
 * 这里实现的是「模拟渠道」，但把真实渠道的麻烦之处（异步通知、验签、重复投递、
 * 金额篡改、钱到了订单却已关闭）全部如实处理掉了 ——
 * <b>面试时可以讲「我实现的是支付链路本身，而不是支付渠道对接」。</b>
 * 详见 {@code docs/06} 里的支付红线说明。
 */
@Tag(name = "24-C端 · 支付", description = "发起支付、查询支付状态（模拟渠道）")
@RestController
@RequestMapping("/api/app/pay")
@RequiredArgsConstructor
public class AppPayController {

    private final PaymentService paymentService;

    @Operation(summary = "发起支付",
            description = "为订单创建支付单；同一「订单+渠道+待支付」会复用已有支付单，刷新收银台不会产生重复单")
    @PostMapping("/create")
    public Result<PaymentVO> create(@Valid @RequestBody PayCreateDTO dto) {
        return Result.success("支付单已创建", paymentService.createPayment(dto.getOrderNo(), dto.getChannel()));
    }

    @Operation(summary = "查询支付状态")
    @GetMapping("/{orderNo}")
    public Result<PaymentVO> query(@PathVariable String orderNo) {
        return Result.success(paymentService.queryByOrderNo(orderNo));
    }
}
