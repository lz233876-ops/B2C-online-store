package com.shopping.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "SPU 查询条件")
public class SpuQuery extends PageQuery {

    @Schema(description = "商品名称，模糊匹配")
    private String spuName;

    @Schema(description = "类目ID。传三级类目可精确匹配；传父类目时由服务层展开为其全部子孙")
    private Long categoryId;

    @Schema(description = "品牌ID")
    private Long brandId;

    @Schema(description = "发布状态：0草稿 1待审 2通过 3驳回")
    private Integer publishStatus;

    @Schema(description = "上下架：1上架 0下架")
    private Integer status;
}
