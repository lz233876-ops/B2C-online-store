package com.shopping.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "品牌查询条件")
public class BrandQuery extends PageQuery {

    @Schema(description = "品牌名，模糊匹配（中英文都试）")
    private String name;

    @Schema(description = "首字母")
    private String firstLetter;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;
}
