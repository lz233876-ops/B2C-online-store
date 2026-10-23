package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.WmsStockLog;

/**
 * 库存流水 Mapper。
 * <p>⚠️ 本表是 append-only：<b>只允许 insert 和 select，禁止 update / delete</b>。
 * 它是财务级凭据，删一条就永远对不上账。
 */
public interface WmsStockLogMapper extends BaseMapper<WmsStockLog> {
}
