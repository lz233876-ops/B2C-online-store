package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.PmsSpu;

/**
 * SPU Mapper。
 * <p>Phase 1 的类目/品牌删除保护需要「查该分类下有没有商品」，
 * 所以先建出来；SPU 自身的 CRUD 在下一轮补全。
 */
public interface PmsSpuMapper extends BaseMapper<PmsSpu> {
}
