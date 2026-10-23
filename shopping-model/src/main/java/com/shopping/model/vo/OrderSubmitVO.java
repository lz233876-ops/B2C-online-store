package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "下单成功返回")
public class OrderSubmitVO {

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "应付金额（分）")
    private Long payAmount;

    @Schema(description = "自动关闭时间，前端据此做「剩余 xx 分 xx 秒」倒计时")
    private java.time.LocalDateTime autoCloseTime;
}
