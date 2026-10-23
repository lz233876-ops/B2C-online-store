package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.PmsSpuAttribute;
import org.apache.ibatis.annotations.Param;

/**
 * SPU 属性关联 Mapper。
 * <p>⚠️ 与 {@link PmsSkuAttributeValueMapper} 同理：<b>物理删除</b>，
 * 因为唯一索引 {@code (spu_id, attribute_id, deleted)} 的两个唯一列都是关联 ID，
 * 且本表数据是纯派生的、每次保存整体重建。
 */
public interface PmsSpuAttributeMapper extends BaseMapper<PmsSpuAttribute> {

    /** 物理删除某 SPU 的全部属性关联（重建前调用） */
    int physicalDeleteBySpuId(@Param("spuId") Long spuId);
}
