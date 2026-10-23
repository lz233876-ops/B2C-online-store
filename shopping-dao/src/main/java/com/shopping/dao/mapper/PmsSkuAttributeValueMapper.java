package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.PmsSkuAttributeValue;
import org.apache.ibatis.annotations.Param;

/**
 * SKU 规格组合 Mapper。
 * <p>
 * ⚠️ <b>本表刻意不用逻辑删除，而是物理删除</b>，理由见 XML 里的注释：
 * 唯一索引 {@code (sku_id, attribute_id, deleted)} 的两个唯一列都是关联 ID，
 * 无法用「删除时改写唯一列」绕开 key 冲突；而本表数据是纯派生的、每次整体重建，
 * 物理删除不丢任何不可再生信息。
 * <p>
 * 结论：<b>「可完全重建的派生数据」用物理删除，「有独立业务含义的数据」才需要逻辑删除保历史。</b>
 */
public interface PmsSkuAttributeValueMapper extends BaseMapper<PmsSkuAttributeValue> {

    /** 物理删除某 SKU 的全部规格组合（重建前调用） */
    int physicalDeleteBySkuId(@Param("skuId") Long skuId);

    /** 物理删除某 SPU 下全部 SKU 的规格组合（删除商品时调用） */
    int physicalDeleteBySpuId(@Param("spuId") Long spuId);
}
