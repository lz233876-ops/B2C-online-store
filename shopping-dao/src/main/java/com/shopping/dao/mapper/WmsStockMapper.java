package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.WmsStock;
import org.apache.ibatis.annotations.Param;

/**
 * 库存 Mapper。
 * <p>
 * <b>四个原子操作全部走条件更新，靠受影响行数表达成功/失败</b>，
 * 绝不允许「先 SELECT 判断再 UPDATE」（并发下必然超卖）。
 * 详见 {@code WmsStockMapper.xml} 里的逐条说明。
 * <p>
 * 返回值的含义统一为：{@code >0} 成功，{@code 0} 前置条件不满足（库存不足 / 没锁过那么多）。
 */
public interface WmsStockMapper extends BaseMapper<WmsStock> {

    /** ① 下单锁定：把可售库存冻结。条件 {@code available_stock >= num} */
    int lockStock(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId, @Param("num") int num);

    /** ② 支付扣减：冻结转实扣（可售库存不变）。条件 {@code locked_stock >= num AND stock >= num} */
    int deductStock(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId, @Param("num") int num);

    /** ③ 取消/超时释放：冻结还回可售。条件 {@code locked_stock >= num} */
    int releaseStock(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId, @Param("num") int num);

    /** ④ 退款回补：货退回，可售库存增加，并计入累计入库 */
    int refundStock(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId, @Param("num") int num);
}
