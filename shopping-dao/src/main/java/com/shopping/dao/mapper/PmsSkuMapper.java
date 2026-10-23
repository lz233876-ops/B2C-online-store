package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.PmsSku;
import org.apache.ibatis.annotations.Param;

public interface PmsSkuMapper extends BaseMapper<PmsSku> {

    /**
     * 逻辑删除，同时把 {@code sku_code} 改写成「原编码_del_主键」。
     * <p>
     * 不能直接用 {@code deleteById}：唯一索引 {@code uk_sku_code (sku_code, deleted)}
     * 在「同一编码被删两次」时会冲突。详见 {@code docs/04-数据库设计.md} §1.7 方案 C。
     *
     * @return 受影响行数
     */
    int softDelete(@Param("id") Long id);
}
