package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "购物车项")
public class CartItemVO {

    @Schema(description = "购物车项ID")
    private Long id;

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "商品名称")
    private String skuName;

    @Schema(description = "商品图片")
    private String skuImage;

    @Schema(description = "规格摘要，如「128GB / 黑色」")
    private String specDesc;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "是否勾选：1勾选 0未勾选")
    private Integer selected;

    @Schema(description = "加入时单价快照（分），仅作参考")
    private Long price;

    @Schema(description = "当前单价（分）。结算以它为准")
    private Long currentPrice;

    @Schema(description = "当前可售库存")
    private Integer availableStock;

    @Schema(description = "小计金额（分）= 当前单价 × 数量")
    private Long amount;

    @Schema(description = "是否失效（下架/删除/售罄/库存不足）")
    private Boolean invalid;

    @Schema(description = "失效原因，前端据此提示")
    private String invalidReason;
}
