package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "类目新增/修改入参")
public class CategoryDTO {

    @Schema(description = "主键；新增时不传，修改时必传")
    private Long id;

    /**
     * 父类目 ID，0 表示一级类目。
     * <p>⚠️ 注意：{@code level} 不在这里 —— 它由父类目派生，前端传了也不采信。
     * 让客户端决定层级会导致「parent 是二级但 level 传了 1」这种数据不一致。
     */
    @Schema(description = "父类目ID，0 表示一级类目", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "父类目不能为空")
    private Long parentId;

    @Schema(description = "类目名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "类目名称不能为空")
    @Size(max = 64, message = "类目名称不能超过 64 字")
    private String name;

    @Schema(description = "排序，越小越靠前", defaultValue = "0")
    @Min(value = 0, message = "排序不能为负数")
    @Max(value = 9999, message = "排序不能超过 9999")
    private Integer sort;

    @Schema(description = "图标URL")
    @Size(max = 500, message = "图标URL过长")
    private String icon;

    @Schema(description = "banner图URL")
    @Size(max = 500, message = "banner图URL过长")
    private String banner;

    @Schema(description = "前台是否展示：1展示 0隐藏", defaultValue = "1")
    private Integer isShow;

    @Schema(description = "状态：1启用 0禁用", defaultValue = "1")
    private Integer status;
}
