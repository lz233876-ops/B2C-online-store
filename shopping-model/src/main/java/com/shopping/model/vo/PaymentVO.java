package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "支付单 / 收银台信息")
public class PaymentVO {

    @Schema(description = "商户支付单号（对应渠道的 out_trade_no）")
    private String paymentNo;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "应付金额（分）")
    private Long amount;

    @Schema(description = "实付金额（分）")
    private Long paidAmount;

    @Schema(description = "渠道：1 模拟支付宝，2 模拟微信")
    private Integer channel;

    @Schema(description = "状态码")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "渠道流水号")
    private String tradeNo;

    @Schema(description = "支付标题")
    private String subject;

    @Schema(description = "支付单过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "支付成功时间")
    private LocalDateTime payTime;

    @Schema(description = "失败/待退款原因")
    private String errorMsg;

    @Schema(description = "模拟收银台地址（真实接入时这里是渠道返回的支付链接/二维码）")
    private String cashierUrl;

    @Schema(description = "调起模拟收银台并完成支付所需的路径（仅本项目演示用）")
    private String mockPayPath;
}
