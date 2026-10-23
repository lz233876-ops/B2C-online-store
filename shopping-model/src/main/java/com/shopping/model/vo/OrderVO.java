package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "订单（列表项）")
public class OrderVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "订单状态码")
    private Integer status;

    @Schema(description = "订单状态描述")
    private String statusDesc;

    @Schema(description = "订单类型：1普通 2秒杀")
    private Integer orderType;

    @Schema(description = "商品总额（分）")
    private Long totalAmount;

    @Schema(description = "运费（分）")
    private Long freightAmount;

    @Schema(description = "优惠合计（分）= 活动+优惠券+积分+会员折扣")
    private Long discountTotal;

    @Schema(description = "应付金额（分）")
    private Long payAmount;

    @Schema(description = "实付金额（分）")
    private Long paidAmount;

    @Schema(description = "下单时间")
    private LocalDateTime createTime;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "自动关闭时间（待付款订单）")
    private LocalDateTime autoCloseTime;

    @Schema(description = "剩余待支付秒数；非待付款状态为 0。前端据此倒计时")
    private Long remainPaySeconds;

    @Schema(description = "收货人姓名（快照）")
    private String receiverName;

    @Schema(description = "收货人手机号（已脱敏）")
    private String receiverPhone;

    @Schema(description = "完整收货地址")
    private String receiverFullAddress;

    @Schema(description = "物流公司")
    private String deliveryCompany;

    @Schema(description = "物流单号")
    private String deliveryNo;

    @Schema(description = "取消原因")
    private String cancelReason;

    @Schema(description = "用户备注")
    private String note;

    @Schema(description = "商品总数（件）")
    private Integer totalQuantity;
}
