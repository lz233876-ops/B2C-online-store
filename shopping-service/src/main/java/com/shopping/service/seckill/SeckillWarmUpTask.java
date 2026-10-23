package com.shopping.service.seckill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 秒杀预热任务。
 * <p>
 * <b>为什么需要周期刷新，而不是只在启动时预热一次：</b>
 * <ul>
 *   <li>应用启动时活动可能还没开始，库存不该提前放出去（提前放会绕过窗口校验）；</li>
 *   <li>运营随时可能新建/修改活动，进程内缓存必须跟着更新；</li>
 *   <li>Redis 里的库存 key 有 TTL，活动时间可能被运营延长，需要续期。</li>
 * </ul>
 * <p>
 * <b>刷新周期 20 秒的取舍：</b>越短越及时，但每次都查一遍活动和秒杀商品表。
 * 20 秒意味着「运营改价后最长 20 秒生效」—— 对秒杀来说可接受，
 * 因为活动配置通常在开始前很久就定好了。
 * <p>
 * ⚠️ <b>预热用 SETNX，不会覆盖已有库存</b>：活动进行中重复预热绝不会把库存重置回满
 * （那会导致超卖）。见 {@link SeckillRedisSupport#warmUpStock}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillWarmUpTask {

    private final SeckillService seckillService;

    /** 默认每 20 秒刷新一次；启动后 5 秒先跑一次，保证应用起来就能抢 */
    @Scheduled(fixedDelayString = "${shopping.seckill.warmup-interval-ms:20000}",
            initialDelayString = "${shopping.seckill.warmup-initial-delay-ms:5000}")
    public void refresh() {
        try {
            seckillService.warmUp();
        } catch (Exception e) {
            // 预热失败不能影响应用运行；下一轮会自动重试
            log.error("秒杀预热任务执行失败（将在下一轮重试）", e);
        }
    }
}
