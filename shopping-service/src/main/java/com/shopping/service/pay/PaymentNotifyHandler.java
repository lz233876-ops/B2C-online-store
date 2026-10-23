package com.shopping.service.pay;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.constant.CommonConstants;
import com.shopping.dao.mapper.OmsOrderItemMapper;
import com.shopping.dao.mapper.OmsOrderMapper;
import com.shopping.dao.mapper.OmsOrderOperateHistoryMapper;
import com.shopping.dao.mapper.PayPaymentMapper;
import com.shopping.model.entity.OmsOrder;
import com.shopping.model.entity.OmsOrderItem;
import com.shopping.model.entity.OmsOrderOperateHistory;
import com.shopping.model.entity.PayPayment;
import com.shopping.model.enums.OrderStatus;
import com.shopping.service.inventory.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 支付回调的<b>业务处理</b>（独立 Bean + 独立事务）。
 * <p>
 * 为什么从 {@code PaymentServiceImpl} 里拆出来：调用的编排层需要
 * 「业务失败时把失败原因写进回调日志」，而回调日志用的是
 * {@code REQUIRES_NEW}。<b>如果业务逻辑和编排层在同一个类里，Spring 的
 * {@code @Transactional} 代理不会生效（自调用绕过代理）</b>，
 * 结果就是「业务异常时日志跟着一起回滚」——那个最需要的日志恰好丢了。
 * <p>
 * <b>本类的执行顺序是有讲究的：</b>
 * <ol>
 *   <li>查支付单、校验金额；</li>
 *   <li>先判断「订单还能不能支付」——因为后面要么推进订单，要么走向退款；</li>
 *   <li><b>条件更新支付单状态</b>（幂等的唯一落点）；</li>
 *   <li>推进订单状态 + 库存从「冻结」转「实扣」。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotifyHandler {

    private static final String ACTION_PAY = "PAY";
    private static final int OPERATOR_SYSTEM = 3;

    /** 渠道通知字段名（对标支付宝的字段命名） */
    public static final String FIELD_TRADE_NO = "trade_no";
    public static final String FIELD_OUT_TRADE_NO = "out_trade_no";
    public static final String FIELD_TOTAL_AMOUNT = "total_amount";
    public static final String FIELD_TRADE_STATUS = "trade_status";
    public static final String FIELD_BUYER = "buyer_logon_id";
    public static final String TRADE_STATUS_SUCCESS = "TRADE_SUCCESS";

    private final PayPaymentMapper paymentMapper;
    private final OmsOrderMapper orderMapper;
    private final OmsOrderItemMapper orderItemMapper;
    private final OmsOrderOperateHistoryMapper historyMapper;
    private final StockService stockService;

    @Transactional(rollbackFor = Exception.class)
    public PayNotifyResult handle(int channel, Map<String, String> params, String rawBody) {
        String paymentNo = params.get(FIELD_OUT_TRADE_NO);
        String tradeNo = params.get(FIELD_TRADE_NO);

        PayPayment payment = paymentMapper.selectOne(Wrappers.<PayPayment>lambdaQuery()
                .eq(PayPayment::getPaymentNo, paymentNo));
        if (payment == null) {
            return PayNotifyResult.fail("支付单不存在：" + paymentNo);
        }
        if (!TRADE_STATUS_SUCCESS.equals(params.get(FIELD_TRADE_STATUS))) {
            // 渠道通知「交易关闭/失败」：不改订单，只把支付单标记失败，用户可重新发起
            paymentMapper.markFailed(paymentNo, "渠道返回状态：" + params.get(FIELD_TRADE_STATUS));
            return PayNotifyResult.ok("已记录渠道的非成功状态：" + params.get(FIELD_TRADE_STATUS));
        }

        // ⚠️ 金额校验：这是防「1 分钱买手机」的关键一步。
        //    只信渠道报的金额、或者只信本地金额都不行 —— 必须比对，不一致直接拒绝。
        long notifyAmount;
        try {
            notifyAmount = Long.parseLong(params.get(FIELD_TOTAL_AMOUNT));
        } catch (Exception e) {
            return PayNotifyResult.fail("金额字段非法：" + params.get(FIELD_TOTAL_AMOUNT));
        }
        if (notifyAmount != payment.getAmount()) {
            log.error("支付回调金额不一致，拒绝处理 | paymentNo={} | 渠道={} | 本地={}",
                    paymentNo, notifyAmount, payment.getAmount());
            return PayNotifyResult.fail("金额不一致：渠道=" + notifyAmount + " 本地=" + payment.getAmount());
        }

        // ---- 先看订单是否还能支付 ----
        OmsOrder order = orderMapper.selectById(payment.getOrderId());
        boolean orderPayable = order != null
                && order.getStatus() != null
                && order.getStatus() == OrderStatus.PENDING_PAYMENT.getCode();

        // ---- 条件更新支付单状态：幂等的唯一落点 ----
        LocalDateTime now = LocalDateTime.now();
        int affected = paymentMapper.markSuccess(paymentNo, tradeNo, params.get(FIELD_BUYER),
                notifyAmount, now, truncate(rawBody, 60000));
        if (affected == 0) {
            // 已经被处理过了（渠道重复投递，且换过 notify_id）。
            // 直接返回成功让它停止重试 —— 【绝不能】在这里再扣一次库存。
            log.info("支付单已处理过，忽略重复回调 | paymentNo={}", paymentNo);
            return PayNotifyResult.duplicate("支付单已处理过（重复回调）");
        }

        if (!orderPayable) {
            // 钱收到了，但订单已经不可支付（用户取消 / 超时关闭）。
            // 这种单子【不能】抛异常回滚 —— 钱确实到账了，支付单状态必须落库，
            // 否则渠道会一直重试、账也永远对不上。正确做法是标记「待退款」，走原路退回。
            String reason = "订单当前状态[" + (order == null ? "不存在" : order.getStatus())
                    + "]不可支付，需原路退款";
            markNeedRefund(payment, reason);
            log.error("⚠️ 收到已关闭订单的支付，需人工/自动退款 | paymentNo={} | orderNo={} | 金额={}",
                    paymentNo, payment.getOrderNo(), notifyAmount);
            return PayNotifyResult.needRefund(reason);
        }

        // ---- 推进订单：待付款 → 待发货 ----
        int orderAffected = orderMapper.updateStatus(payment.getOrderId(),
                OrderStatus.PENDING_PAYMENT.getCode(), OrderStatus.PENDING_DELIVERY.getCode(),
                now, null, null, null, null, null, channel, notifyAmount, null, null);
        if (orderAffected == 0) {
            // 极小概率：在「判断可支付」与「推进订单」之间被取消了。
            // 同样不能抛异常（会回滚支付单状态），标记待退款。
            String reason = "订单状态并发变更，需原路退款";
            markNeedRefund(payment, reason);
            log.error("⚠️ 推进订单失败（并发），需退款 | paymentNo={} | orderNo={}",
                    paymentNo, payment.getOrderNo());
            return PayNotifyResult.needRefund(reason);
        }

        // ---- 库存：从「冻结」转「实扣」 ----
        // 顺序不能反：必须订单确实推进成功了才动库存。
        // 如果先扣库存再推进订单而订单失败了，库存就白扣了（而且没有订单可对账）。
        List<OmsOrderItem> items = orderItemMapper.selectList(Wrappers.<OmsOrderItem>lambdaQuery()
                .eq(OmsOrderItem::getOrderId, payment.getOrderId()));
        for (OmsOrderItem item : items) {
            stockService.deductStock(item.getSkuId(), item.getQuantity(),
                    payment.getOrderNo(), payment.getPaymentNo());
        }

        writeHistory(payment.getOrderId(), payment.getOrderNo(), ACTION_PAY,
                OrderStatus.PENDING_PAYMENT.getCode(), OrderStatus.PENDING_DELIVERY.getCode(),
                "支付成功：" + paymentNo);

        log.info("支付回调处理成功 | paymentNo={} | orderNo={} | 金额={} | 渠道={}",
                paymentNo, payment.getOrderNo(), notifyAmount, channel);
        return PayNotifyResult.ok("支付成功，订单已进入待发货");
    }

    private void writeHistory(Long orderId, String orderNo, String action,
                              int fromStatus, int toStatus, String note) {
        OmsOrderOperateHistory history = new OmsOrderOperateHistory();
        history.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        history.setOrderId(orderId);
        history.setOrderNo(orderNo);
        history.setAction(action);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setOperatorType(OPERATOR_SYSTEM);
        history.setOperatorName("支付渠道");
        history.setNote(note);
        history.setTraceId(MDC.get("traceId"));
        historyMapper.insert(history);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 标记「这笔支付需要原路退款」。
     * <p>⚠️ <b>不能</b>用 {@code markFailed}：它的条件也是 {@code status = 0}，
     * 而此刻支付单已经被条件更新成「支付成功」了，那条 SQL 会静默影响 0 行，
     * 原因根本记不下来 —— 这种「以为记了实际没记」的坑，只有在真的发生
     * 「订单已关闭但钱到了」时才会暴露。所以这里直接按主键更新 {@code error_msg}。
     */
    private void markNeedRefund(PayPayment payment, String reason) {
        PayPayment flag = new PayPayment();
        flag.setId(payment.getId());
        flag.setErrorMsg(reason);
        paymentMapper.updateById(flag);
    }
}
