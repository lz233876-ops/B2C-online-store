package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 属性详情（属性本体 + 它的属性值列表）。
 * <p>属性值内联返回而不是让前端再发一次请求：一次弹窗编辑需要完整数据，
 * 分成两个接口只会增加往返和前端的状态同步负担。
 */
@Data
@Schema(description = "属性（含属性值）")
public class AttributeVO {

    @Schema(description = "属性ID")
    private Long id;

    @Schema(description = "所属类目ID")
    private Long categoryId;

    @Schema(description = "属性名")
    private String name;

    @Schema(description = "类型：1规格属性 2参数属性")
    private Integer type;

    @Schema(description = "录入方式：1单选 2多选 3手工输入")
    private Integer inputType;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "是否必填：1是 0否")
    private Integer isRequired;

    @Schema(description = "是否可筛选：1是 0否")
    private Integer searchable;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;

    @Schema(description = "属性值列表")
    private List<AttributeValueVO> values = new ArrayList<>();
}
