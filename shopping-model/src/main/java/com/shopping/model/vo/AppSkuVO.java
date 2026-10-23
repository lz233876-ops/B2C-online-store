package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * C 端 SKU。
 * <p><b>⚠️ 刻意没有 {@code costPrice}（成本价）。</b>详见 {@link AppSpuVO} 的说明。
 */
@Data
@Schema(description = "C 端 SKU（不含成本价）")
public class AppSkuVO {

    @Schema(description = "SKU ID")
    private Long id;

    @Schema(description = "SKU 名称")
    private String skuName;

    @Schema(description = "规格摘要，如「128GB / 黑色」")
    private String specDesc;

    @Schema(description = "规格值明细，便于前端渲染规格选择器")
    private java.util.List<SpecValue> specValues;

    @Schema(description = "图片")
    private String image;

    @Schema(description = "售价（分）")
    private Long price;

    @Schema(description = "售价（元）")
    private String priceYuan;

    @Schema(description = "原价（分）")
    private Long originalPrice;

    @Schema(description = "可售库存")
    private Integer availableStock;

    @Data
    @Schema(description = "规格值")
    public static class SpecValue {
        @Schema(description = "属性ID")
        private Long attributeId;
        @Schema(description = "属性名")
        private String attributeName;
        @Schema(description = "属性值ID")
        private Long valueId;
        @Schema(description = "属性值")
        private String valueName;
    }
}
