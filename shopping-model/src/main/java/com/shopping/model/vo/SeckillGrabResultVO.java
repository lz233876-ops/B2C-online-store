package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "抢购结果（同步返回，订单异步创建）")
public class SeckillGrabResultVO {

    /** 抢购结果枚举值，与 {@code SeckillGrabStatus} 对应：1 排队中，2 已抢到，3 失败 */
    @Schema(description = "结果码：1排队中 2已抢到 3失败")
    private Integer code;

    @Schema(description = "结果描述")
    private String message;

    @Schema(description = "请求唯一ID；前端用它轮询抢购结果")
    private String requestId;

    @Schema(description = "剩余库存（Redis 实时值，抢购成功时返回）")
    private Integer remainStock;

    @Schema(description = "订单号；异步创建完成前为 null，前端需轮询")
    private String orderNo;
}
