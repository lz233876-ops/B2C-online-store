package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "抢购结果查询（前端轮询用）")
public class SeckillResultVO {

    @Schema(description = "是否已抢到")
    private Boolean grabbed;

    @Schema(description = "是否仍在处理中（已抢到名额但订单还没建好）")
    private Boolean processing;

    @Schema(description = "记录状态：0已抢到待支付 1已支付 2已取消/超时 3已退款")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "订单号（未建好前为 null）")
    private String orderNo;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "秒杀成交价（分）")
    private Long seckillPrice;

    @Schema(description = "抢到时间")
    private LocalDateTime createTime;
}
