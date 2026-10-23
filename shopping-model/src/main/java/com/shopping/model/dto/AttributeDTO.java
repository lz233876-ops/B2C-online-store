package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 属性新增/修改入参（<b>聚合保存</b>：属性本体 + 它的全部属性值）。
 * <p>
 * 为什么把属性值一起提交，而不是分成「先存属性、再逐个存值」两个接口：
 * 属性值脱离了属性没有意义，前端在编辑弹窗里也是同时改完再点保存的。
 * 分两个接口会带来「属性存了、值没存成功」的中间态，需要额外处理回滚。
 * <b>一个业务动作对应一个接口</b>，把它的原子性交给一个事务，是最省心的做法。
 */
@Data
@Schema(description = "属性新增/修改入参")
public class AttributeDTO {

    @Schema(description = "主键；新增不传，修改必传")
    private Long id;

    @Schema(description = "所属类目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属类目不能为空")
    private Long categoryId;

    @Schema(description = "属性名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "属性名不能为空")
    @Size(max = 64, message = "属性名不能超过 64 字")
    private String name;

    @Schema(description = "类型：1规格属性（参与SKU生成） 2参数属性（仅展示）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "属性类型不能为空")
    @Min(value = 1, message = "属性类型只能是 1 或 2")
    @Max(value = 2, message = "属性类型只能是 1 或 2")
    private Integer type;

    @Schema(description = "录入方式：1单选 2多选 3手工输入", defaultValue = "1")
    private Integer inputType;

    @Schema(description = "单位，如 GB / 英寸")
    @Size(max = 16, message = "单位不能超过 16 字")
    private String unit;

    @Schema(description = "是否必填：1是 0否", defaultValue = "0")
    private Integer isRequired;

    @Schema(description = "是否可筛选：1是 0否", defaultValue = "0")
    private Integer searchable;

    @Schema(description = "排序", defaultValue = "0")
    private Integer sort;

    @Schema(description = "状态：1启用 0禁用", defaultValue = "1")
    private Integer status;

    /**
     * 属性值列表。
     * <p><b>规格属性必须至少有一个值</b>，否则前端没法生成 SKU；
     * 参数属性可以为空（值来自 SPU 上的自由文本）。
     * 这个校验在 Service 层做——注解表达不了「type=1 时 size>=1」这种条件约束。
     */
    @Schema(description = "属性值列表（规格属性必填）")
    @Valid
    private List<AttributeValueItem> values;

    @Data
    @Schema(description = "属性值项")
    public static class AttributeValueItem {

        @Schema(description = "主键；新增不传")
        private Long id;

        @Schema(description = "属性值", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "属性值不能为空")
        @Size(max = 128, message = "属性值不能超过 128 字")
        private String value;

        @Schema(description = "排序", defaultValue = "0")
        private Integer sort;

        @Schema(description = "状态：1启用 0禁用", defaultValue = "1")
        private Integer status;
    }
}
