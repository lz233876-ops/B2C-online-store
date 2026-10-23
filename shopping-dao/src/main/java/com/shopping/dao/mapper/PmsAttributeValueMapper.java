package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.PmsAttributeValue;
import org.apache.ibatis.annotations.Param;

public interface PmsAttributeValueMapper extends BaseMapper<PmsAttributeValue> {

    /**
     * 逻辑删除，同时把 {@code value} 改写成「原值_del_主键」。
     * <p>
     * 理由同 {@link PmsAttributeMapper#softDelete}：唯一索引
     * {@code uk_attribute_id_value (attribute_id, value, deleted)} 会在
     * 「同一属性下同一个值被删两次」时冲突。详见 {@code docs/04-数据库设计.md} §1.7 方案 C。
     *
     * @return 受影响行数
     */
    int softDelete(@Param("id") Long id);
}
