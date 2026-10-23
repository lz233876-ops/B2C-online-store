package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "勾选/取消勾选购物车项入参")
public class CartSelectDTO {

    /**
     * 要操作的购物车项 ID。
     * <p><b>传空或 null 表示「全部」</b>——对应前端「全选/取消全选」。
     * 这个语义要在文档里说清，否则前端会以为空 = 什么都不做。
     */
    @Schema(description = "购物车项ID列表；传空表示全部")
    private List<Long> ids;

    @Schema(description = "是否勾选：1勾选 0取消", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "勾选状态不能为空")
    @Min(value = 0, message = "勾选状态只能是 0 或 1")
    @Max(value = 1, message = "勾选状态只能是 0 或 1")
    private Integer selected;
}
