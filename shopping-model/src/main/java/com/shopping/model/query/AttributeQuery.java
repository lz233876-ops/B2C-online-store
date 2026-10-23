package com.shopping.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 属性查询条件。
 * <p>属性列表通常按类目 + 类型过滤，不需要分页（一个类目的属性最多几十条），
 * 所以这里继承 PageQuery 只是为了「需要时可以翻页」，前端默认传大一点的 size。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "属性查询条件")
public class AttributeQuery extends PageQuery {

    @Schema(description = "所属类目ID")
    private Long categoryId;

    @Schema(description = "类型：1规格属性 2参数属性")
    private Integer type;

    @Schema(description = "属性名，模糊匹配")
    private String name;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;
}
