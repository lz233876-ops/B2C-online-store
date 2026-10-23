package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * SKU 详情项（<b>后台管理端使用</b>）。
 * <p>
 * ⚠️ <b>本 VO 含 {@code costPrice}（成本价），绝不能在 C 端接口里复用。</b>
 * C 端需要单独的 VO，把成本价<b>从类型上</b>排除掉——比"记得在代码里置 null"可靠。
 * 这是本项目的一条约定：敏感字段的暴露边界由类型表达，不靠人工记忆。
 */
@Data
@Schema(description = "SKU 详情项（含成本价，仅后台）")
public class SkuVO {

    @Schema(description = "SKU ID")
    private Long id;

    @Schema(description = "SKU 编码")
    private String skuCode;

    @Schema(description = "SKU 名称")
    private String skuName;

    @Schema(description = "规格摘要，如「128GB / 黑色」")
    private String specDesc;

    @Schema(description = "规格组合明细")
    private List<SkuSpecVO> specValues = new ArrayList<>();

    @Schema(description = "SKU 图片")
    private String image;

    @Schema(description = "销售价（分）")
    private Long price;

    @Schema(description = "划线价（分）")
    private Long originalPrice;

    @Schema(description = "成本价（分）—— 仅后台可见")
    private Long costPrice;

    @Schema(description = "可售库存（冗余展示值）")
    private Integer stock;

    @Schema(description = "锁定库存")
    private Integer lockedStock;

    @Schema(description = "累计销量")
    private Integer saleCount;

    @Schema(description = "重量（克）")
    private Integer weight;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;

    @Data
    @Schema(description = "SKU 的单个规格值")
    public static class SkuSpecVO {
        @Schema(description = "属性ID")
        private Long attributeId;

        @Schema(description = "属性名，如「颜色」")
        private String attributeName;

        @Schema(description = "属性值ID")
        private Long valueId;

        @Schema(description = "属性值，如「黑色」")
        private String valueName;
    }
}
