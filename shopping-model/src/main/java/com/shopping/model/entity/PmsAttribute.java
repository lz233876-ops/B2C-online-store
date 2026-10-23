package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品属性（{@code pms_attribute}）。
 * <p>
 * <b>这张表最关键的一个字段是 {@code type}，它把两种属性硬分开：</b>
 * <ul>
 *   <li>{@code type = 1} <b>规格属性</b> —— 参与 SKU 生成。选了「颜色 + 容量」，
 *       就要生成 颜色数 × 容量数 个 SKU。取值来自 {@link PmsAttributeValue}。</li>
 *   <li>{@code type = 2} <b>参数属性</b> —— <b>不参与 SKU 生成</b>，只在详情页的
 *       参数表展示（如「上市年份：2023」）。值存在 {@code pms_spu_attribute.value_json}。</li>
 * </ul>
 * <b>为什么必须分开：</b>如果参数属性也参与 SKU 生成，那「上市年份」会让商品
 * 凭空多出 N 个 SKU，<b>SKU 数量指数爆炸</b>。这是新手建模最常犯的错。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_attribute")
public class PmsAttribute extends BaseEntity {

    /** 所属类目 ID。属性挂在类目上，同类目下的商品复用同一套属性 */
    private Long categoryId;

    /** 属性名，如「颜色」「上市年份」 */
    private String name;

    /** 属性类型：1 规格属性（参与 SKU 生成），2 参数属性（仅展示） */
    private Integer type;

    /**
     * 录入方式：1 从属性值列表单选，2 多选，3 手工输入。
     * <p>规格属性通常是 1（必须从预定义值里选，否则没法生成 SKU）；
     * 参数属性可能是 3（自由文本，如「保修期」）。
     */
    private Integer inputType;

    /** 单位，如「GB」「英寸」 */
    private String unit;

    /** 是否必填：1 是，0 否。规格属性一般必填 */
    private Integer isRequired;

    /** 是否可用于搜索筛选：1 是，0 否 */
    private Integer searchable;

    /** 排序，值越小越靠前 */
    private Integer sort;

    /** 状态：1 启用，0 禁用 */
    private Integer status;
}
