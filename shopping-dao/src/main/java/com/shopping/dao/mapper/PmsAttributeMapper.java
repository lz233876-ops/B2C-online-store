package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.PmsAttribute;
import org.apache.ibatis.annotations.Param;

public interface PmsAttributeMapper extends BaseMapper<PmsAttribute> {

    /**
     * 逻辑删除，同时把 {@code name} 改写成「原名_del_主键」。
     * <p>
     * 不能直接用 {@code deleteById}：唯一索引 {@code uk_category_id_name (category_id, name, deleted)}
     * 里 {@code deleted} 只有 0/1 两个值，同一类目下已有同名已删行时会撞唯一键。
     * 详见 {@code docs/04-数据库设计.md} §1.7 方案 C。
     *
     * @return 受影响行数；0 表示记录不存在或已被删除
     */
    int softDelete(@Param("id") Long id);
}
