package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SKU-属性值组合（{@code pms_sku_attribute_value}）。
 * <p>
 * <b>这是「SKU 由哪些规格值组合而成」的规范表示。</b>一个 SKU 有 N 个规格属性就有 N 行，
 * 例如「iPhone 15 / 黑色 / 128G」对应两行：颜色=黑色、容量=128G。
 * <p>
 * <b>为什么冗余 {@code spuId} / {@code attributeName} / {@code attributeValue}：</b>
 * 详情页最核心的查询是「给我这个 SPU 的全部规格组合，渲染规格选择器」。
 * 冗余 {@code spuId} 后一条 SQL 零 JOIN 就能拿到；不冗余就必须先 JOIN {@code pms_sku}
 * 拿到 spu_id（而 {@code pms_sku} 是 20+ 列的宽表，回表成本不低）。
 * <b>详情页是全站 QPS 最高的页面，这一次 JOIN 省得很值。</b>
 * <p>
 * 冗余的代价是「改属性名要同步刷两处」，收益是查询路径最短——典型的空间换时间。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_sku_attribute_value")
public class PmsSkuAttributeValue extends BaseEntity {

    /** SKU ID */
    private Long skuId;

    /** 冗余的 SPU ID，用于零 JOIN 查询某商品的全部规格组合 */
    private Long spuId;

    /** 规格属性 ID */
    private Long attributeId;

    /** 冗余的属性名快照，如「颜色」 */
    private String attributeName;

    /** 属性值 ID */
    private Long attributeValueId;

    /** 冗余的属性值快照，如「黑色」 */
    private String attributeValue;

    /** 排序 */
    private Integer sort;
}
