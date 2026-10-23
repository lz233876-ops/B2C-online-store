package com.shopping.service.seckill;

/**
 * 秒杀建单结果。
 *
 * @param created   是否本次真的创建了订单
 * @param duplicate 是否是重复消息（幂等命中，无需处理）
 * @param orderNo   订单号（created 时非空）
 * @param reason    说明
 */
public record SeckillOrderResult(boolean created, boolean duplicate, String orderNo, String reason) {

    public static SeckillOrderResult created(String orderNo) {
        return new SeckillOrderResult(true, false, orderNo, "建单成功");
    }

    public static SeckillOrderResult duplicate(String reason) {
        return new SeckillOrderResult(false, true, null, reason);
    }
}
