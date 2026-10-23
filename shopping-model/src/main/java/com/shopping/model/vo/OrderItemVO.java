package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "订单明细项（全部是下单时的快照，不随商品变化）")
public class OrderItemVO {

    @Schema(description = "明细ID")
    private Long id;

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "商品名称（快照）")
    private String spuName;

    @Schema(description = "SKU 名称（快照）")
    private String skuName;

    @Schema(description = "商品图片（快照）")
    private String skuImage;

    @Schema(description = "规格摘要（快照）")
    private String specDesc;

    @Schema(description = "SKU 编码（快照）")
    private String skuCode;

    @Schema(description = "成交单价（分，快照）")
    private Long price;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "小计（分）")
    private Long totalAmount;

    @Schema(description = "本项实付（分）")
    private Long payAmount;

    @Schema(description = "退款状态：0未退款 1部分退款 2全额退款")
    private Integer refundStatus;
}
