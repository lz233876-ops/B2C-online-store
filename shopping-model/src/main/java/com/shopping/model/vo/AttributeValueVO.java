package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "属性值项")
public class AttributeValueVO {

    @Schema(description = "属性值ID")
    private Long id;

    @Schema(description = "所属属性ID")
    private Long attributeId;

    @Schema(description = "属性值")
    private String value;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;
}
