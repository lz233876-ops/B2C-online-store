package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * C 端商品详情 = 商品主体 + 图片 + 详情 + SKU 列表。
 * <p><b>⚠️ 不含成本价。</b>详见 {@link AppSpuVO}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "C 端商品详情")
public class AppSpuDetailVO extends AppSpuVO {

    @Schema(description = "相册图片")
    private List<String> albumImages = new ArrayList<>();

    @Schema(description = "详情（HTML）")
    private String detailHtml;

    @Schema(description = "SKU 列表")
    private List<AppSkuVO> skus = new ArrayList<>();

    @Schema(description = "规格属性（仅 type=1 的规格属性，用于渲染选择器）")
    private List<SpecAttribute> specAttributes = new ArrayList<>();

    @Data
    @Schema(description = "规格属性及其可选值")
    public static class SpecAttribute {
        @Schema(description = "属性ID")
        private Long attributeId;
        @Schema(description = "属性名，如「颜色」")
        private String attributeName;
        @Schema(description = "可选值")
        private List<SpecValueItem> values = new ArrayList<>();
    }

    @Data
    @Schema(description = "规格可选值")
    public static class SpecValueItem {
        @Schema(description = "值ID")
        private Long valueId;
        @Schema(description = "值名，如「黑色」")
        private String valueName;
    }
}
