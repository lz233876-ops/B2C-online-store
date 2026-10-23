package com.shopping.service.seckill.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.PmsSkuMapper;
import com.shopping.dao.mapper.SmsSeckillActivityMapper;
import com.shopping.dao.mapper.SmsSeckillRecordMapper;
import com.shopping.dao.mapper.SmsSeckillSkuMapper;
import com.shopping.model.entity.PmsSku;
import com.shopping.model.entity.SmsSeckillActivity;
import com.shopping.model.entity.SmsSeckillRecord;
import com.shopping.model.entity.SmsSeckillSku;
import com.shopping.model.vo.SeckillActivityVO;
import com.shopping.model.vo.SeckillGrabResultVO;
import com.shopping.model.vo.SeckillResultVO;
import com.shopping.model.vo.SeckillSkuVO;
import com.shopping.service.auth.security.SecurityUtils;
import com.shopping.service.mq.SeckillMessage;
import com.shopping.service.mq.SeckillMessageProducer;
import com.shopping.service.seckill.SeckillRedisSupport;
import com.shopping.service.seckill.SeckillService;
import com.shopping.service.seckill.SeckillSkuRuntime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 秒杀服务实现。
 * <p>
 * <b>抢购路径上刻意不做任何数据库操作</b>：本地缓存校验窗口 → 1 次 Redis Lua → 1 次 MQ 投递。
 * 数据库只在「异步建单」与「活动列表」里被访问。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    /** 一人一单标记的 TTL 缓冲：活动结束后再多留 1 小时，避免边界上重复抢 */
    private static final Duration USER_MARK_BUFFER = Duration.ofHours(1);
    /** 库存 key 的 TTL 缓冲，同上 */
    private static final Duration STOCK_TTL_BUFFER = Duration.ofHours(2);

    /** 进程内秒杀商品缓存（见 {@link SeckillSkuRuntime} 的多实例说明） */
    private final Map<Long, SeckillSkuRuntime> runtimeCache = new ConcurrentHashMap<>();

    private final SmsSeckillActivityMapper activityMapper;
    private final SmsSeckillSkuMapper seckillSkuMapper;
    private final SmsSeckillRecordMapper recordMapper;
    private final PmsSkuMapper skuMapper;
    private final SeckillRedisSupport redisSupport;
    private final SeckillMessageProducer messageProducer;

    /* ==================== 预热 ==================== */

    @Override
    public int warmUp() {
        long nowMs = System.currentTimeMillis();
        // 清理已过期的缓存，避免进程内缓存随着活动数量无限增长
        runtimeCache.entrySet().removeIf(e -> e.getValue().expired(nowMs));

        // 进行中 + 即将开始的活动（status != 3 已下线）；结束时间已过的不要
        List<SmsSeckillActivity> activities = activityMapper.selectList(
                Wrappers.<SmsSeckillActivity>lambdaQuery()
                        .ne(SmsSeckillActivity::getStatus, 3)
                        .gt(SmsSeckillActivity::getEndTime, LocalDateTime.now())
                        .orderByAsc(SmsSeckillActivity::getStartTime));
        int warmed = 0;
        for (SmsSeckillActivity activity : activities) {
            List<SmsSeckillSku> skus = seckillSkuMapper.selectList(
                    Wrappers.<SmsSeckillSku>lambdaQuery()
                            .eq(SmsSeckillSku::getActivityId, activity.getId())
                            .eq(SmsSeckillSku::getStatus, 1));
            for (SmsSeckillSku sku : skus) {
                SeckillSkuRuntime runtime = toRuntime(activity, sku);
                runtimeCache.put(sku.getId(), runtime);

                int remain = Math.max(nzInt(sku.getSeckillStock()) - nzInt(sku.getSeckillSold()), 0);
                Duration ttl = durationUntilEnd(runtime);
                boolean written = redisSupport.warmUpStock(sku.getId(), remain, ttl);
                if (written) {
                    warmed++;
                    log.info("秒杀库存已预热 | seckillSkuId={} | activityId={} | 剩余={} | TTL={}s",
                            sku.getId(), activity.getId(), remain, ttl.toSeconds());
                }
            }
        }
        log.info("秒杀预热完成 | 活动数={} | 本次新写入库存的秒杀商品数={} | 缓存条目={}",
                activities.size(), warmed, runtimeCache.size());
        return warmed;
    }

    /* ==================== 抢购 ==================== */

    @Override
    public SeckillGrabResultVO grab(Long seckillSkuId, String ip) {
        Long userId = SecurityUtils.getAppUserId();

        // ---- 1. 本地缓存校验活动窗口（无 IO）----
        SeckillSkuRuntime runtime = runtimeCache.computeIfAbsent(seckillSkuId, this::loadRuntime);
        if (runtime == null) {
            throw new BizException(ResultCode.SECKILL_NOT_WARMED);
        }
        long nowMs = System.currentTimeMillis();
        if (nowMs < runtime.startMs()) {
            throw new BizException(ResultCode.SECKILL_NOT_STARTED);
        }
        if (nowMs >= runtime.endMs()) {
            throw new BizException(ResultCode.SECKILL_ENDED);
        }
        // MQ 不可用时明确报错，而不是「假装抢到」——后者会让用户白等
        if (!messageProducer.isAvailable()) {
            throw new BizException(ResultCode.SECKILL_CHANNEL_UNAVAILABLE);
        }

        // ---- 2. Redis 原子抢购（1 次 RTT）----
        String requestId = UUID.randomUUID().toString().replace("-", "");
        int qty = 1;
        long userMarkTtl = durationUntilEnd(runtime).plus(USER_MARK_BUFFER).toSeconds();
        long result = redisSupport.tryGrab(seckillSkuId, runtime.activityId(), userId, qty, userMarkTtl);

        if (result == SeckillRedisSupport.RESULT_USER_REPEAT) {
            throw new BizException(ResultCode.SECKILL_REPEAT);
        }
        if (result == SeckillRedisSupport.RESULT_SOLD_OUT) {
            throw new BizException(ResultCode.SECKILL_SOLD_OUT);
        }
        if (result == SeckillRedisSupport.RESULT_NOT_WARMED) {
            // 活动窗口已到但库存 key 不在：说明预热没跑到（比如应用刚重启）
            // 立刻补一次预热，让下一个请求能正常抢，同时本次明确报错
            log.warn("秒杀库存未预热，尝试即时预热 | seckillSkuId={}", seckillSkuId);
            warmUpSingle(seckillSkuId, runtime);
            throw new BizException(ResultCode.SECKILL_NOT_WARMED);
        }

        // ---- 3. 发 MQ（同步发送，为了能据此回滚）----
        SeckillMessage message = new SeckillMessage();
        message.setActivityId(runtime.activityId());
        message.setSeckillSkuId(seckillSkuId);
        message.setSkuId(runtime.skuId());
        message.setUserId(userId);
        message.setQuantity(qty);
        message.setSeckillPrice(runtime.seckillPrice());
        message.setRequestId(requestId);
        message.setIp(ip);
        message.setGrabTime(nowMs);
        try {
            messageProducer.send(message);
        } catch (Exception e) {
            // ⚠️ 消息没发出去 = 这笔秒杀不会有人去建单。
            // 必须把 Redis 预扣的库存和一人一单标记回滚，否则那件商品永远卖不出去。
            redisSupport.rollback(seckillSkuId, runtime.activityId(), userId, qty);
            log.error("秒杀消息投递失败，已回滚占位 | seckillSkuId={} | userId={}", seckillSkuId, userId, e);
            throw new BizException(ResultCode.SECKILL_CHANNEL_UNAVAILABLE);
        }

        SeckillGrabResultVO vo = new SeckillGrabResultVO();
        vo.setCode(1);
        vo.setMessage("抢购成功，订单创建中");
        vo.setRequestId(requestId);
        vo.setRemainStock((int) result);
        log.info("秒杀抢到名额 | userId={} | seckillSkuId={} | requestId={} | 剩余={}",
                userId, seckillSkuId, requestId, result);
        return vo;
    }

    /* ==================== 结果查询 ==================== */

    @Override
    public SeckillResultVO queryResult(Long activityId) {
        Long userId = SecurityUtils.getAppUserId();
        SmsSeckillRecord record = recordMapper.selectOne(Wrappers.<SmsSeckillRecord>lambdaQuery()
                .eq(SmsSeckillRecord::getActivityId, activityId)
                .eq(SmsSeckillRecord::getUserId, userId)
                .last("LIMIT 1"));
        SeckillResultVO vo = new SeckillResultVO();
        if (record == null) {
            vo.setGrabbed(false);
            vo.setProcessing(false);
            return vo;
        }
        vo.setGrabbed(true);
        vo.setStatus(record.getStatus());
        vo.setOrderId(record.getOrderId());
        vo.setOrderNo(record.getOrderNo());
        vo.setSeckillPrice(record.getSeckillPrice());
        vo.setCreateTime(record.getCreateTime());
        // orderNo 为空 = 座位已占、订单还在 MQ 后面排队 —— 前端应继续轮询
        vo.setProcessing(record.getOrderNo() == null);
        vo.setStatusDesc(switch (record.getStatus() == null ? -1 : record.getStatus()) {
            case 0 -> "已抢到，待支付";
            case 1 -> "已支付";
            case 2 -> "已取消";
            case 3 -> "已退款";
            default -> "处理中";
        });
        return vo;
    }

    /* ==================== 活动查询 ==================== */

    @Override
    public List<SeckillActivityVO> listRunning() {
        List<SmsSeckillActivity> activities = activityMapper.selectList(
                Wrappers.<SmsSeckillActivity>lambdaQuery()
                        .in(SmsSeckillActivity::getStatus, 0, 1)
                        .gt(SmsSeckillActivity::getEndTime, LocalDateTime.now())
                        .orderByAsc(SmsSeckillActivity::getStartTime));
        return activities.stream().map(a -> toActivityVO(a, loadSkus(a.getId()))).toList();
    }

    @Override
    public SeckillActivityVO detail(Long activityId) {
        SmsSeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.SECKILL_NOT_STARTED, "活动不存在");
        }
        return toActivityVO(activity, loadSkus(activityId));
    }

    @Override
    public int cachedSkuCount() {
        return runtimeCache.size();
    }

    /* ==================== 内部方法 ==================== */

    /** 懒加载：缓存未命中时查一次库（并由调用方决定是否预热库存） */
    private SeckillSkuRuntime loadRuntime(Long seckillSkuId) {
        SmsSeckillSku sku = seckillSkuMapper.selectById(seckillSkuId);
        if (sku == null) {
            return null;
        }
        SmsSeckillActivity activity = activityMapper.selectById(sku.getActivityId());
        if (activity == null) {
            return null;
        }
        return toRuntime(activity, sku);
    }

    /** 即时预热单个秒杀商品（预热任务没跑到时的兜底） */
    private void warmUpSingle(Long seckillSkuId, SeckillSkuRuntime runtime) {
        SmsSeckillSku sku = seckillSkuMapper.selectById(seckillSkuId);
        if (sku == null) {
            return;
        }
        int remain = Math.max(nzInt(sku.getSeckillStock()) - nzInt(sku.getSeckillSold()), 0);
        redisSupport.warmUpStock(seckillSkuId, remain, durationUntilEnd(runtime));
    }

    private List<SmsSeckillSku> loadSkus(Long activityId) {
        return seckillSkuMapper.selectList(Wrappers.<SmsSeckillSku>lambdaQuery()
                .eq(SmsSeckillSku::getActivityId, activityId)
                .eq(SmsSeckillSku::getStatus, 1)
                .orderByAsc(SmsSeckillSku::getSort));
    }

    private SeckillActivityVO toActivityVO(SmsSeckillActivity activity, List<SmsSeckillSku> skus) {
        SeckillActivityVO vo = new SeckillActivityVO();
        vo.setId(activity.getId());
        vo.setActivityName(activity.getActivityName());
        vo.setDescription(activity.getDescription());
        vo.setBanner(activity.getBanner());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setStatus(activity.getStatus());
        vo.setStatusDesc(statusDesc(activity));

        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            vo.setRemainSeconds(Duration.between(now, activity.getStartTime()).getSeconds());
        } else if (activity.getEndTime() != null) {
            vo.setRemainSeconds(Math.max(Duration.between(now, activity.getEndTime()).getSeconds(), 0));
        } else {
            vo.setRemainSeconds(0L);
        }

        if (skus.isEmpty()) {
            return vo;
        }
        // 批量取 SKU 快照，避免逐个查询
        List<Long> skuIds = skus.stream().map(SmsSeckillSku::getSkuId).toList();
        Map<Long, PmsSku> skuMap = skuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(PmsSku::getId, Function.identity(), (a, b) -> a));
        List<SeckillSkuVO> skuVos = new ArrayList<>(skus.size());
        for (SmsSeckillSku s : skus) {
            SeckillSkuVO sv = new SeckillSkuVO();
            sv.setSeckillSkuId(s.getId());
            sv.setSkuId(s.getSkuId());
            sv.setSpuId(s.getSpuId());
            sv.setSeckillPrice(s.getSeckillPrice());
            sv.setOriginalPrice(s.getOriginalPrice());
            sv.setSeckillStock(s.getSeckillStock());
            sv.setSeckillSold(s.getSeckillSold());
            sv.setLimitPerUser(s.getLimitPerUser());
            sv.setSeckillPriceYuan(yuan(s.getSeckillPrice()));
            sv.setOriginalPriceYuan(yuan(s.getOriginalPrice()));
            sv.setDiscount(discount(s.getSeckillPrice(), s.getOriginalPrice()));
            // 剩余库存以 Redis 为准（DB 的 sold 是异步更新的，会滞后）
            sv.setRemainStock(redisSupport.getRemainStock(s.getId()));
            PmsSku sku = skuMap.get(s.getSkuId());
            if (sku != null) {
                sv.setSkuName(sku.getSkuName());
                sv.setSkuImage(sku.getImage());
                sv.setSpecDesc(sku.getSpecDesc());
            }
            skuVos.add(sv);
        }
        skuVos.sort(Comparator.comparing(SeckillSkuVO::getSeckillPrice, Comparator.nullsLast(Comparator.naturalOrder())));
        vo.setSkus(skuVos);
        return vo;
    }

    private static String statusDesc(SmsSeckillActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        if (Integer.valueOf(3).equals(activity.getStatus())) {
            return "已下线";
        }
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            return "即将开始";
        }
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            return "已结束";
        }
        return "进行中";
    }

    private static SeckillSkuRuntime toRuntime(SmsSeckillActivity activity, SmsSeckillSku sku) {
        ZoneId zone = ZoneId.systemDefault();
        long startMs = activity.getStartTime() == null ? 0L
                : activity.getStartTime().atZone(zone).toInstant().toEpochMilli();
        long endMs = activity.getEndTime() == null ? Long.MAX_VALUE
                : activity.getEndTime().atZone(zone).toInstant().toEpochMilli();
        return new SeckillSkuRuntime(
                sku.getId(), activity.getId(), sku.getSkuId(),
                nz(sku.getSeckillPrice()), startMs, endMs,
                sku.getLimitPerUser() == null ? 1 : sku.getLimitPerUser());
    }

    private static Duration durationUntilEnd(SeckillSkuRuntime runtime) {
        long remainMs = runtime.endMs() - System.currentTimeMillis();
        if (remainMs <= 0) {
            return STOCK_TTL_BUFFER;
        }
        return Duration.ofMillis(remainMs).plus(STOCK_TTL_BUFFER);
    }

    private static String yuan(Long fen) {
        if (fen == null) {
            return "0.00";
        }
        return BigDecimal.valueOf(fen).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 折扣，如「5.0 折」（秒杀价 / 原价 × 10） */
    private static String discount(Long seckillPrice, Long originalPrice) {
        if (seckillPrice == null || originalPrice == null || originalPrice <= 0) {
            return null;
        }
        return BigDecimal.valueOf(seckillPrice)
                .multiply(BigDecimal.TEN)
                .divide(BigDecimal.valueOf(originalPrice), 1, RoundingMode.HALF_UP)
                .toPlainString() + " 折";
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static int nzInt(Integer v) {
        return v == null ? 0 : v;
    }
}
