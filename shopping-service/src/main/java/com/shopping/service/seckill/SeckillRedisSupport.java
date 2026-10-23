package com.shopping.service.seckill;

import com.shopping.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 秒杀的 Redis 原子操作。
 * <p>
 * <b>全部逻辑压进一个 Lua 脚本，只占 1 次 Redis 往返</b>（RTT）。这一点对秒杀至关重要：
 * 如果分成「先查用户 → 再查库存 → 再扣减」三次往返，即使每次只有 0.5ms，
 * 在 5000 QPS 下也会让 Redis 连接池和网络成为瓶颈；
 * 更重要的是<b>三次往返之间会有竞态</b>（超卖/一人多单）。
 * <p>
 * Lua 在 Redis 中是<b>单线程原子执行</b>的，等于把「判断 + 扣减 + 记录用户」
 * 变成了一个不可分割的操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillRedisSupport {

    /** 返回值：>=0 表示抢购成功，值为剩余库存 */
    public static final long RESULT_NOT_WARMED = -1L;
    public static final long RESULT_USER_REPEAT = -2L;
    public static final long RESULT_SOLD_OUT = -3L;

    /**
     * 抢购脚本。
     * <pre>
     * KEYS[1] = 库存 key
     * KEYS[2] = 一人一单 key
     * ARGV[1] = 购买数量
     * ARGV[2] = 一人一单 key 的 TTL（秒）
     *
     * 返回： 剩余库存(>=0) | -1 未预热 | -2 已参与过 | -3 已售罄
     * </pre>
     * <b>顺序是有讲究的：先查用户、再查库存。</b>
     * 先判断用户可以把「重复点击」的流量挡在库存扣减之前 ——
     * 一个用户狂点 100 次，只有第 1 次会碰库存，其余 99 次在第一步就返回了。
     */
    private static final String GRAB_SCRIPT = """
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return -2
            end
            local stock = redis.call('GET', KEYS[1])
            if not stock then
                return -1
            end
            stock = tonumber(stock)
            local qty = tonumber(ARGV[1])
            if stock < qty then
                return -3
            end
            redis.call('SET', KEYS[2], '1', 'EX', tonumber(ARGV[2]))
            redis.call('DECRBY', KEYS[1], qty)
            return stock - qty
            """;

    private static final DefaultRedisScript<Long> GRAB = new DefaultRedisScript<>(GRAB_SCRIPT, Long.class);

    private final StringRedisTemplate redis;

    /**
     * 原子抢购。
     *
     * @param userKeyTtlSeconds 一人一单标记的存活时间，应当覆盖整个活动周期
     */
    public long tryGrab(Long seckillSkuId, Long activityId, Long userId, int qty, long userKeyTtlSeconds) {
        Long result = redis.execute(GRAB,
                List.of(RedisKeys.seckillStock(seckillSkuId), RedisKeys.seckillUser(activityId, userId)),
                String.valueOf(qty), String.valueOf(Math.max(userKeyTtlSeconds, 60)));
        return result == null ? RESULT_NOT_WARMED : result;
    }

    /**
     * 预热：把「总库存 - 已售」写入 Redis。
     * <p>⚠️ <b>只在 key 不存在时写（SETNX 语义）。</b>如果无条件覆盖，活动进行中
     * 一次预热就会把已经被扣掉的库存「重置」回满，直接导致<b>超卖</b>。
     * 这是预热逻辑最容易写错的地方。
     *
     * @return true 表示本次真的写入了；false 表示已预热过（跳过）
     */
    public boolean warmUpStock(Long seckillSkuId, int remainStock, Duration ttl) {
        Boolean written = redis.opsForValue().setIfAbsent(
                RedisKeys.seckillStock(seckillSkuId), String.valueOf(Math.max(remainStock, 0)), ttl);
        return Boolean.TRUE.equals(written);
    }

    /** 读取实时剩余库存；返回 null 表示未预热 */
    public Integer getRemainStock(Long seckillSkuId) {
        String value = redis.opsForValue().get(RedisKeys.seckillStock(seckillSkuId));
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("秒杀库存值非法，按未预热处理 | key={} | value={}", seckillSkuId, value);
            return null;
        }
    }

    /**
     * 回滚（补偿）：把库存加回去，并释放一人一单标记。
     * <p><b>只在「Redis 预扣成功但后续一定失败」时调用</b> ——
     * 典型场景：MQ 发送失败、建单时发现没有收货地址。
     * 不补偿的后果是那件商品<b>永远卖不出去</b>（库存被永久占住），比超卖还难发现。
     */
    public void rollback(Long seckillSkuId, Long activityId, Long userId, int qty) {
        try {
            redis.opsForValue().increment(RedisKeys.seckillStock(seckillSkuId), qty);
            redis.delete(RedisKeys.seckillUser(activityId, userId));
            log.warn("已回滚秒杀占位 | seckillSkuId={} | userId={} | qty={}", seckillSkuId, userId, qty);
        } catch (Exception e) {
            // 回滚失败是最坏的情况（库存永久泄漏），必须打 ERROR 让人工介入
            log.error("⚠️ 秒杀占位回滚失败，需人工核对 | seckillSkuId={} | userId={} | qty={}",
                    seckillSkuId, userId, qty, e);
        }
    }

    /** 清理一人一单标记（仅用于测试/运维重置） */
    public void resetUserMark(Long activityId, Long userId) {
        redis.delete(RedisKeys.seckillUser(activityId, userId));
    }
}
