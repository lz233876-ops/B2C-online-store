package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.OmsCartItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车 Mapper。
 * <p>⚠️ 本表用<b>物理删除</b>（见 XML 与实体类注释）。所有删除都必须带 {@code user_id} 条件。
 */
public interface OmsCartItemMapper extends BaseMapper<OmsCartItem> {

    /** 按 ID 列表删除当前会员的购物车项（带 user_id 条件，防越权） */
    int physicalDeleteByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    /** 按 SKU 列表删除当前会员的购物车项（下单成功后清理已购商品用） */
    int physicalDeleteBySkuIds(@Param("userId") Long userId, @Param("skuIds") List<Long> skuIds);
}
