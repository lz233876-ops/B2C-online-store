package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "加入购物车入参")
public class CartAddDTO {

    @Schema(description = "SKU ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "SKU ID 不能为空")
    private Long skuId;

    /**
     * 购买数量。
     * <p>上限 200 是业务规则（单个 SKU 一单最多买 200 件），
     * 同时也是防止「数量传个 20 亿」把金额算溢出。
     */
    @Schema(description = "购买数量，1~200", requiredMode = Schema.RequiredMode.REQUIRED, defaultValue = "1")
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 200, message = "单个商品最多购买 200 件")
    private Integer quantity;
}
