package com.shopping.service.seckill;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.constant.CommonConstants;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.OmsOrderItemMapper;
import com.shopping.dao.mapper.OmsOrderMapper;
import com.shopping.dao.mapper.OmsOrderOperateHistoryMapper;
import com.shopping.dao.mapper.PmsSkuMapper;
import com.shopping.dao.mapper.PmsSpuMapper;
import com.shopping.dao.mapper.SmsSeckillRecordMapper;
import com.shopping.dao.mapper.SmsSeckillSkuMapper;
import com.shopping.dao.mapper.UmsUserAddressMapper;
import com.shopping.model.entity.OmsOrder;
import com.shopping.model.entity.OmsOrderItem;
import com.shopping.model.entity.OmsOrderOperateHistory;
import com.shopping.model.entity.PmsSku;
import com.shopping.model.entity.PmsSpu;
import com.shopping.model.entity.SmsSeckillRecord;
import com.shopping.model.entity.SmsSeckillSku;
import com.shopping.model.entity.UmsUserAddress;
import com.shopping.model.enums.OrderStatus;
import com.shopping.service.inventory.StockService;
import com.shopping.service.mq.SeckillMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 秒杀订单创建器（MQ 消费者调用，<b>独立 Bean + 独立事务</b>）。
 * <p>
 * 为什么独立成 Bean：消费者需要在建单失败时做 Redis 补偿，
 * 而建单事务必须完整回滚。<b>如果建单和补偿在同一个方法里，事务边界会互相污染。</b>
 * <p>
 * <b>幂等的三层保证（这是秒杀最容易被问的点）：</b>
 * <table border="1">
 *   <tr><th>层</th><th>防的是什么</th></tr>
 *   <tr><td>Redis {@code SET} 一人一单</td><td>重复流量（挡住 99%，但不保证，Redis 会丢数据）</td></tr>
 *   <tr><td>{@code uk_activity_id_user_id}</td><td><b>一人一单的强约束</b>（唯一索引，不依赖 Redis）</td></tr>
 *   <tr><td>{@code uk_request_id}</td><td><b>消息重复投递</b>（RocketMQ 保证 at-least-once，一定会重复）</td></tr>
 * </table>
 * 注意：<b>记录行必须在建订单之前插入</b>。如果先建订单再插记录，
 * 那么一条重复消息会先把订单建出来，然后插记录时才撞唯一键 —— 订单已经多出来了，
 * 事务回滚能救回来，但会白白让 MQ 重试、日志里全是假错误。
 * <b>先占位、再干活</b>才是正确的顺序。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderCreator {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ACTION_CREATE = "CREATE";
    private static final int OPERATOR_USER = 1;
    private static final int SPU_ON_SALE = 1;
    private static final int SPU_PUBLISHED = 2;

    private final SmsSeckillRecordMapper recordMapper;
    private final SmsSeckillSkuMapper seckillSkuMapper;
    private final PmsSkuMapper skuMapper;
    private final PmsSpuMapper spuMapper;
    private final UmsUserAddressMapper addressMapper;
    private final OmsOrderMapper orderMapper;
    private final OmsOrderItemMapper orderItemMapper;
    private final OmsOrderOperateHistoryMapper historyMapper;
    private final StockService stockService;

    @Value("${shopping.order.auto-close-minutes:30}")
    private int autoCloseMinutes;

    /**
     * 创建秒杀订单。<b>整个方法一个事务</b>：记录、订单、明细、库存、流水要么全成要么全败。
     *
     * @throws BizException 业务性失败（无地址、商品下架、库存不足）—— 调用方据此回滚 Redis
     */
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrderResult create(SeckillMessage msg) {
        // ---- 1. 先占位：插入抢购记录（同时占住 requestId 与 activity+user 两个唯一键）----
        SmsSeckillRecord record = new SmsSeckillRecord();
        record.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        record.setActivityId(msg.getActivityId());
        record.setSeckillSkuId(msg.getSeckillSkuId());
        record.setSkuId(msg.getSkuId());
        record.setUserId(msg.getUserId());
        record.setQuantity(msg.getQuantity());
        record.setSeckillPrice(msg.getSeckillPrice());
        record.setStatus(0);
        record.setRequestId(msg.getRequestId());
        record.setIp(msg.getIp());
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 同一条消息被重复投递（MQ 的 at-least-once 语义下这一定会发生），
            // 或者同一用户重复抢购。两种情况都只需安静地返回「已处理」。
            log.info("秒杀消息幂等命中，跳过 | requestId={} | userId={} | activityId={}",
                    msg.getRequestId(), msg.getUserId(), msg.getActivityId());
            return SeckillOrderResult.duplicate("重复消息或重复抢购");
        }

        // ---- 2. 校验秒杀商品与底层商品仍在售 ----
        SmsSeckillSku seckillSku = seckillSkuMapper.selectById(msg.getSeckillSkuId());
        if (seckillSku == null || !Integer.valueOf(1).equals(seckillSku.getStatus())) {
            throw new BizException(ResultCode.SECKILL_ENDED, "秒杀商品已下架");
        }
        // 以【数据库】的价格为准：消息里的价格只是快照，若被打过则不能按消息里的价卖
        long seckillPrice = seckillSku.getSeckillPrice() == null ? 0L : seckillSku.getSeckillPrice();
        if (msg.getSeckillPrice() != null && !msg.getSeckillPrice().equals(seckillPrice)) {
            log.warn("秒杀价与消息快照不一致，以数据库为准 | seckillSkuId={} | 消息={} | 库={}",
                    msg.getSeckillSkuId(), msg.getSeckillPrice(), seckillPrice);
        }

        PmsSku sku = skuMapper.selectById(seckillSku.getSkuId());
        if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
            throw new BizException(ResultCode.SKU_NOT_FOUND, "秒杀商品已下架");
        }
        PmsSpu spu = spuMapper.selectById(sku.getSpuId());
        if (spu == null || !Integer.valueOf(SPU_ON_SALE).equals(spu.getStatus())
                || !Integer.valueOf(SPU_PUBLISHED).equals(spu.getPublishStatus())) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND, "秒杀商品已下架");
        }

        // ---- 3. 收货地址（秒杀用的是用户默认地址）----
        UmsUserAddress address = defaultAddressOf(msg.getUserId());
        if (address == null) {
            // 这是最常见的建单失败原因：用户没填地址就抢。必须回滚 Redis 让名额释放
            throw new BizException(ResultCode.ADDRESS_NOT_FOUND, "请先在商城填写收货地址再参与秒杀");
        }

        // ---- 4. 数据库兜底扣减已售数量（防 Redis 数据漂移导致超卖）----
        int soldAffected = seckillSkuMapper.increaseSold(seckillSku.getId(), msg.getQuantity());
        if (soldAffected == 0) {
            log.error("⚠️ 秒杀已售数量已达上限，Redis 与数据库不一致 | seckillSkuId={}", seckillSku.getId());
            throw new BizException(ResultCode.SECKILL_SOLD_OUT, "已售罄");
        }

        // ---- 5. 建订单（orderType=2 秒杀订单）----
        LocalDateTime now = LocalDateTime.now();
        long totalAmount = seckillPrice * msg.getQuantity();

        OmsOrder order = new OmsOrder();
        order.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        order.setOrderNo(generateOrderNo());
        order.setUserId(msg.getUserId());
        order.setOrderType(2);            // 2 = 秒杀订单，便于统计与售后区分
        order.setSourceType(2);
        order.setTotalAmount(totalAmount);
        // 秒杀默认包邮：运费另算会让「秒杀价」的对比失去意义
        order.setFreightAmount(0L);
        order.setDiscountAmount(0L);
        order.setCouponAmount(0L);
        order.setPointsAmount(0L);
        order.setMemberDiscountAmount(0L);
        order.setPayAmount(totalAmount);
        order.setPaidAmount(0L);
        order.setUsePoints(0);
        order.setGainPoints(0);
        order.setGainGrowth(0);
        order.setPayType(0);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setReceiverProvince(address.getProvince());
        order.setReceiverCity(address.getCity());
        order.setReceiverDistrict(address.getDistrict());
        order.setReceiverDetailAddress(address.getDetailAddress());
        order.setNote("秒杀订单");
        order.setVersion(0);
        order.setAutoCloseTime(now.plusMinutes(autoCloseMinutes));
        orderMapper.insert(order);

        // ---- 6. 明细（价格用秒杀价）----
        OmsOrderItem item = new OmsOrderItem();
        item.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        item.setOrderId(order.getId());
        item.setOrderNo(order.getOrderNo());
        item.setUserId(msg.getUserId());
        item.setSpuId(sku.getSpuId());
        item.setSkuId(sku.getId());
        item.setSpuName(spu.getSpuName());
        item.setSkuName(sku.getSkuName());
        item.setSkuImage(sku.getImage());
        item.setSpecDesc(sku.getSpecDesc());
        item.setSkuCode(sku.getSkuCode());
        item.setPrice(seckillPrice);
        item.setQuantity(msg.getQuantity());
        item.setTotalAmount(totalAmount);
        item.setDiscountAmount(0L);
        item.setPayAmount(totalAmount);
        item.setRefundStatus(0);
        item.setRefundAmount(0L);
        item.setIsCommented(0);
        orderItemMapper.insert(item);

        // ---- 7. 锁定真实库存（秒杀投放量应当已从真实库存里预留）----
        // 用锁定而不是扣减：和普通订单保持一致 —— 支付成功才真正扣减（见 PaymentNotifyHandler）
        stockService.lockStock(sku.getId(), msg.getQuantity(), order.getOrderNo());

        // ---- 8. 回填记录的订单号 + 写状态流水 ----
        SmsSeckillRecord recordUpdate = new SmsSeckillRecord();
        recordUpdate.setId(record.getId());
        recordUpdate.setOrderId(order.getId());
        recordUpdate.setOrderNo(order.getOrderNo());
        recordMapper.updateById(recordUpdate);

        OmsOrderOperateHistory history = new OmsOrderOperateHistory();
        history.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        history.setOrderId(order.getId());
        history.setOrderNo(order.getOrderNo());
        history.setAction(ACTION_CREATE);
        history.setFromStatus(-1);
        history.setToStatus(OrderStatus.PENDING_PAYMENT.getCode());
        history.setOperatorType(OPERATOR_USER);
        history.setOperatorId(msg.getUserId());
        history.setOperatorName("秒杀");
        history.setNote("秒杀下单成功，秒杀价 " + seckillPrice + " 分");
        history.setTraceId(MDC.get("traceId"));
        historyMapper.insert(history);

        log.info("秒杀建单成功 | orderNo={} | userId={} | 秒杀价={} | requestId={}",
                order.getOrderNo(), msg.getUserId(), seckillPrice, msg.getRequestId());
        return SeckillOrderResult.created(order.getOrderNo());
    }

    /**
     * 取默认地址；没有默认地址则退化为「最近添加的一条」。
     * <p>秒杀场景不能因为「用户忘了设默认地址」就不让买 ——
     * 有地址就用，选哪条在秒杀里不重要（用户后续可改）。
     */
    private UmsUserAddress defaultAddressOf(Long userId) {
        UmsUserAddress address = addressMapper.selectOne(Wrappers.<UmsUserAddress>lambdaQuery()
                .eq(UmsUserAddress::getUserId, userId)
                .eq(UmsUserAddress::getDefaultStatus, 1)
                .last("LIMIT 1"));
        if (address != null) {
            return address;
        }
        return addressMapper.selectOne(Wrappers.<UmsUserAddress>lambdaQuery()
                .eq(UmsUserAddress::getUserId, userId)
                .orderByDesc(UmsUserAddress::getId)
                .last("LIMIT 1"));
    }

    private String generateOrderNo() {
        return "SO" + LocalDateTime.now().format(NO_DATE) + IdWorker.getId();
    }
}
