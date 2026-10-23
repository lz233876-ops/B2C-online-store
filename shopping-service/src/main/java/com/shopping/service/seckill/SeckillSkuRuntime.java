package com.shopping.service.seckill;

/**
 * 秒杀商品的运行时快照（进程内缓存）。
 * <p>
 * <b>为什么要缓存：</b>抢购接口是全场最热的路径，如果每个请求都去查
 * {@code sms_seckill_sku} + {@code sms_seckill_activity} 两张表（哪怕走主键索引），
 * 在 5000 QPS 下数据库也会被打出明显的 CPU 峰值，而这些数据<b>在活动期间几乎不变</b>。
 * <p>
 * <b>为什么不用 Redis 缓存活动信息：</b>抢购热路径已经有一次 Redis 往返（Lua 脚本），
 * 再加一次 GET 就翻倍了。活动窗口/价格这类「几秒内不会变」的数据放进程内最划算。
 * <p>
 * ⚠️ <b>多实例部署时这份缓存会不一致</b>（各实例刷新时间不同）：
 * 最长不一致时间 = 刷新周期（本项目 20 秒）。代价是「活动边界前后几秒可能误判」，
 * 而<b>库存与一人一单的强一致仍由 Redis 原子脚本保证</b>，所以不会超卖、不会一人多单。
 * 真实高要求场景可在活动开始瞬间用 Redis 时间戳做二次校验。
 */
public record SeckillSkuRuntime(
        Long seckillSkuId,
        Long activityId,
        Long skuId,
        Long seckillPrice,
        /** 活动开始时间（毫秒） */
        long startMs,
        /** 活动结束时间（毫秒） */
        long endMs,
        int limitPerUser) {

    /** 活动是否已过期（缓存淘汰用） */
    public boolean expired(long nowMs) {
        return nowMs > endMs;
    }

    /** 当前是否在活动窗口内 */
    public boolean inWindow(long nowMs) {
        return nowMs >= startMs && nowMs < endMs;
    }
}
