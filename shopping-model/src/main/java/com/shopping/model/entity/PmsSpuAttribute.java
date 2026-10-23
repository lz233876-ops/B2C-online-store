package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SPU-属性关联（{@code pms_spu_attribute}）。
 * <p>
 * 记录「这个 SPU 用了哪些属性」：
 * <ul>
 *   <li>{@code type = 1} 规格属性 —— 它的值集合决定了要生成多少个 SKU。
 *       {@code valueJson} 存该 SPU 选中的属性值数组，如 {@code ["黑色","白色","原色"]}；</li>
 *   <li>{@code type = 2} 参数属性 —— {@code valueJson} 存参数值，如 {@code "2023-09"}。
 *       参数属性<b>不参与 SKU 生成</b>，只用于详情页的参数表。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_spu_attribute")
public class PmsSpuAttribute extends BaseEntity {

    /** SPU ID */
    private Long spuId;

    /** 属性 ID */
    private Long attributeId;

    /** 冗余的属性名快照 */
    private String attributeName;

    /** 属性类型：1 规格属性，2 参数属性 */
    private Integer type;

    /** 属性值，JSON。规格属性存数组，参数属性存单值或数组 */
    private String valueJson;

    /** 排序 */
    private Integer sort;
}
