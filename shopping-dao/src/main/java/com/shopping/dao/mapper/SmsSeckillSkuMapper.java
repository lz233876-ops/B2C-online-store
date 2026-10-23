package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.SmsSeckillSku;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SmsSeckillSkuMapper extends BaseMapper<SmsSeckillSku> {

    /**
     * <b>数据库层的兜底闸门</b>：累加已售数量，且保证不会超过总库存。
     * <p>条件 {@code seckill_sold + qty <= seckill_stock} 与库存中心的
     * {@code available_stock >= num} 是同一个思路 —— <b>判断与扣减在同一条 SQL 里完成</b>。
     * <p>正常情况下这条 SQL 永远不会失败（Redis 预扣已经挡住了超额流量），
     * 它存在的意义是：<b>万一 Redis 数据丢了/漂移了，数据库不会超卖。</b>
     *
     * @return 1 成功；0 表示已售罄（Redis 与 DB 不一致，需要人工核对）
     */
    @Update("""
            UPDATE sms_seckill_sku
               SET seckill_sold = seckill_sold + #{qty}, update_time = NOW()
             WHERE id = #{id}
               AND deleted = 0
               AND seckill_sold + #{qty} <= seckill_stock
            """)
    int increaseSold(@Param("id") Long id, @Param("qty") int qty);

    /** 回滚已售数量（建单失败时用，注意不能减成负数） */
    @Update("""
            UPDATE sms_seckill_sku
               SET seckill_sold = seckill_sold - #{qty}, update_time = NOW()
             WHERE id = #{id}
               AND deleted = 0
               AND seckill_sold >= #{qty}
            """)
    int decreaseSold(@Param("id") Long id, @Param("qty") int qty);
}
