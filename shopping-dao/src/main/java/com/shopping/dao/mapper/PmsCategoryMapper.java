package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.PmsCategory;

/**
 * 商品类目 Mapper。
 * <p>树形结构的查询走单表全量 + 内存组装（类目总量在千级以内），
 * 不用递归 SQL——递归 CTE 在 MySQL 8 虽然支持，但对这种小数据量是过度设计。
 */
public interface PmsCategoryMapper extends BaseMapper<PmsCategory> {
}
