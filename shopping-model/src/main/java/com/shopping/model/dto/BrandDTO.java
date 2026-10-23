package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "品牌新增/修改入参")
public class BrandDTO {

    @Schema(description = "主键；新增不传，修改必传")
    private Long id;

    @Schema(description = "品牌英文名（唯一）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "品牌名称不能为空")
    @Size(max = 64, message = "品牌名称不能超过 64 字")
    private String name;

    @Schema(description = "品牌中文名")
    @Size(max = 64, message = "品牌中文名不能超过 64 字")
    private String nameCn;

    @Schema(description = "品牌logo URL")
    @Size(max = 500, message = "logo URL 过长")
    private String logo;

    /**
     * 首字母必须是单个字母。
     * <p>用正则约束而不是放任自由文本：这个字段是用来做「按字母索引导航」的，
     * 一旦混进「AB」「1」这类值，前端的分组逻辑就乱了。
     */
    @Schema(description = "首字母，单个大写字母 A~Z")
    @Pattern(regexp = "^[A-Za-z]?$", message = "首字母必须是单个字母")
    private String firstLetter;

    @Schema(description = "品牌介绍")
    @Size(max = 1000, message = "品牌介绍不能超过 1000 字")
    private String description;

    @Schema(description = "排序，越小越靠前", defaultValue = "0")
    private Integer sort;

    @Schema(description = "状态：1启用 0禁用", defaultValue = "1")
    private Integer status;
}
