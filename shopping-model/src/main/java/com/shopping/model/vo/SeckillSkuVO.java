package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "秒杀商品")
public class SeckillSkuVO {

    @Schema(description = "秒杀商品ID（抢购时用它，不是 skuId）")
    private Long seckillSkuId;

    @Schema(description = "SKU ID")
    private Long skuId;

    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "商品名称")
    private String skuName;

    @Schema(description = "商品图片")
    private String skuImage;

    @Schema(description = "规格摘要")
    private String specDesc;

    @Schema(description = "秒杀价（分）")
    private Long seckillPrice;

    @Schema(description = "秒杀价（元，展示用）")
    private String seckillPriceYuan;

    @Schema(description = "原价（分）")
    private Long originalPrice;

    @Schema(description = "原价（元，展示用）")
    private String originalPriceYuan;

    @Schema(description = "秒杀总库存")
    private Integer seckillStock;

    @Schema(description = "已售数量")
    private Integer seckillSold;

    @Schema(description = "剩余可抢数量（来自 Redis 实时值，为 null 表示未预热）")
    private Integer remainStock;

    @Schema(description = "折扣，如「5.0 折」")
    private String discount;

    @Schema(description = "每人限购")
    private Integer limitPerUser;
}
