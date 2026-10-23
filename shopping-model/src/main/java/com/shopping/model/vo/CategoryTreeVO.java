package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "类目树节点")
public class CategoryTreeVO {

    @Schema(description = "类目ID")
    private Long id;

    @Schema(description = "父类目ID")
    private Long parentId;

    @Schema(description = "类目名称")
    private String name;

    @Schema(description = "层级：1/2/3")
    private Integer level;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "图标URL")
    private String icon;

    @Schema(description = "banner图URL")
    private String banner;

    @Schema(description = "前台是否展示：1展示 0隐藏")
    private Integer isShow;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;

    @Schema(description = "子类目")
    private List<CategoryTreeVO> children = new ArrayList<>();
}
