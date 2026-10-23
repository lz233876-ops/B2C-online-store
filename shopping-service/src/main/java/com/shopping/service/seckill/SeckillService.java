package com.shopping.service.seckill;

import com.shopping.model.vo.SeckillActivityVO;
import com.shopping.model.vo.SeckillGrabResultVO;
import com.shopping.model.vo.SeckillResultVO;

import java.util.List;

/**
 * 秒杀服务。
 * <p>
 * <b>核心设计：同步只做 Redis，异步才落库。</b>
 * <pre>
 *   抢购请求 ──> [同步] 本地缓存校验活动窗口
 *                     → Redis Lua 原子「校验一人一单 + 扣库存」（1 次 RTT）
 *                     → 发 MQ
 *                     → 立即返回「排队中」
 *
 *   MQ ─────> [异步] 幂等校验 → 建秒杀订单 → 落记录 → 锁真实库存
 *
 *   前端 ────> 轮询 /result 查抢购结果
 * </pre>
 * <b>为什么必须这样切：</b>建订单要写 3~4 张表（订单、明细、记录、库存流水）并加行锁，
 * 单笔耗时 5~20ms。如果同步做，5000 并发下数据库连接池瞬间打满，
 * 响应时间飙升到秒级，用户看到的就是「转圈然后失败」。
 * <b>把落库搬到 MQ 后面，同步路径只剩 Redis + 一次消息投递，吞吐提升一个数量级。</b>
 * <p>
 * 代价是「最终一致」：用户抢到后要等几百毫秒才能看到订单。
 * 这个代价是秒杀场景的标准取舍 —— 用户体验用「排队中」的文案弥补。
 */
public interface SeckillService {

    /**
     * 预热：把「进行中 / 即将开始」的活动的秒杀库存写进 Redis，并刷新进程内缓存。
     *
     * @return 预热的秒杀商品数量
     */
    int warmUp();

    /**
     * 抢购。
     *
     * @param seckillSkuId 秒杀商品 ID（不是 skuId —— 同一个 SKU 可能出现在多个活动里）
     * @param ip           客户端 IP，落到记录表供风控排查（Service 层不碰 HttpServletRequest）
     */
    SeckillGrabResultVO grab(Long seckillSkuId, String ip);

    /** 查询我在某个活动里的抢购结果（前端轮询用） */
    SeckillResultVO queryResult(Long activityId);

    /** 进行中与即将开始的活动列表（含实时剩余库存） */
    List<SeckillActivityVO> listRunning();

    /** 活动详情 */
    SeckillActivityVO detail(Long activityId);

    /** 当前进程内缓存了多少个秒杀商品（运维/验收观察用） */
    int cachedSkuCount();
}
