package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "修改购物车数量入参")
public class CartUpdateDTO {

    @Schema(description = "购物车项ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "购物车项ID不能为空")
    private Long id;

    @Schema(description = "新数量，0 表示删除该项，1~200", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数量不能为空")
    @Min(value = 0, message = "数量不能为负")
    @Max(value = 200, message = "单个商品最多购买 200 件")
    private Integer quantity;
}
