package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 属性值（{@code pms_attribute_value}）。
 * <p>
 * 如「颜色」下面的 黑色 / 白色 / 原色。规格属性的值就是 SKU 笛卡尔积的输入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_attribute_value")
public class PmsAttributeValue extends BaseEntity {

    /** 所属属性 ID */
    private Long attributeId;

    /** 属性值，如「黑色」 */
    private String value;

    /** 排序，值越小越靠前 */
    private Integer sort;

    /** 状态：1 启用，0 禁用 */
    private Integer status;
}
