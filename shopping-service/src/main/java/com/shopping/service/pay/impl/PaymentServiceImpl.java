package com.shopping.service.pay.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.constant.CommonConstants;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.OmsOrderMapper;
import com.shopping.dao.mapper.PayPaymentMapper;
import com.shopping.model.entity.OmsOrder;
import com.shopping.model.entity.PayPayment;
import com.shopping.model.entity.PayNotifyLog;
import com.shopping.model.enums.OrderStatus;
import com.shopping.model.enums.PaymentStatus;
import com.shopping.model.vo.PaymentVO;
import com.shopping.service.auth.security.SecurityUtils;
import com.shopping.service.pay.MockPayGateway;
import com.shopping.service.pay.PayNotifyLogRecorder;
import com.shopping.service.pay.PayNotifyResult;
import com.shopping.service.pay.PaymentNotifyHandler;
import com.shopping.service.pay.PaymentService;
import com.shopping.service.pay.config.PayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    /** 渠道：1 模拟支付宝，2 模拟微信 */
    public static final int CHANNEL_MOCK_ALIPAY = 1;
    public static final int CHANNEL_MOCK_WECHAT = 2;

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 渠道通知类型：1 支付结果，2 退款结果 */
    private static final int NOTIFY_TYPE_PAY = 1;

    /**
     * 验签失败的通知在落库时给 notify_id 加的后缀。
     * <p>目的是<b>不让失败的通知占用渠道真正的 notify_id</b>：
     * 否则伪造通知可以先「占坑」，把渠道随后发来的真通知挤成「重复」而丢弃，导致丢单。
     */
    private static final String INVALID_LOG_SUFFIX = ":INVALID";

    private final PayPaymentMapper paymentMapper;
    private final OmsOrderMapper orderMapper;
    private final MockPayGateway gateway;
    private final PayNotifyLogRecorder notifyLogRecorder;
    private final PaymentNotifyHandler notifyHandler;
    private final PayProperties payProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO createPayment(String orderNo, Integer channel) {
        int payChannel = normalizeChannel(channel);
        Long userId = SecurityUtils.getAppUserId();

        OmsOrder order = orderMapper.selectOne(Wrappers.<OmsOrder>lambdaQuery()
                .eq(OmsOrder::getOrderNo, orderNo)
                .eq(OmsOrder::getUserId, userId));
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PENDING_PAYMENT.getCode()) {
            throw new BizException(ResultCode.PAY_ORDER_NOT_PAYABLE,
                    "订单当前状态为「" + OrderStatus.of(order.getStatus()).getDesc() + "」，无需支付");
        }

        // 同一个「订单 + 渠道 + 待支付」只应有一条支付单（唯一索引 uk_order_id_channel_status 保证）。
        // 这里先查再插是为了【复用】而不是报错：用户刷新收银台不应该产生新支付单。
        PayPayment existing = paymentMapper.selectOne(Wrappers.<PayPayment>lambdaQuery()
                .eq(PayPayment::getOrderId, order.getId())
                .eq(PayPayment::getChannel, payChannel)
                .eq(PayPayment::getStatus, PaymentStatus.PENDING.getCode()));
        if (existing != null) {
            return toVO(existing);
        }

        PayPayment payment = new PayPayment();
        payment.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        payment.setPaymentNo(generatePaymentNo());
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(userId);
        payment.setAmount(order.getPayAmount());
        payment.setPaidAmount(0L);
        payment.setChannel(payChannel);
        payment.setStatus(PaymentStatus.PENDING.getCode());
        payment.setSubject("商城订单 " + order.getOrderNo());
        payment.setExpireTime(LocalDateTime.now().plusMinutes(payProperties.getExpireMinutes()));
        payment.setVersion(0);
        paymentMapper.insert(payment);

        log.info("创建支付单 | paymentNo={} | orderNo={} | 金额={} | 渠道={}",
                payment.getPaymentNo(), orderNo, payment.getAmount(), payChannel);
        return toVO(payment);
    }

    @Override
    public PaymentVO queryByOrderNo(String orderNo) {
        Long userId = SecurityUtils.getAppUserId();
        PayPayment payment = paymentMapper.selectOne(Wrappers.<PayPayment>lambdaQuery()
                .eq(PayPayment::getOrderNo, orderNo)
                .eq(PayPayment::getUserId, userId)
                .orderByDesc(PayPayment::getId)
                .last("LIMIT 1"));
        if (payment == null) {
            throw new BizException(ResultCode.PAYMENT_NOT_FOUND);
        }
        return toVO(payment);
    }

    /**
     * 回调编排：<b>验签 → 留痕 → 业务 → 回写日志</b>。
     * <p>本方法<b>刻意不加 {@code @Transactional}</b>：业务的成败与日志的留存是两件事，
     * 日志必须用独立事务落库（见 {@link PayNotifyLogRecorder}）。
     * <p>还刻意把业务异常<b>转成 failure 回执而不是抛出去</b>：
     * 抛出会让渠道收到 500，有些渠道把它当成「系统故障」并加大重试频率；
     * 明确返回 failure 才是渠道协议里约定的「请重试」信号。
     * <p>
     * <b>⚠️ 顺序上有一个必须守住的原则：验签必须在「按 notify_id 去重」之前。</b>
     * 第一版把去重放在验签前面，结果是：
     * <ol>
     *   <li>一条签名错误的伪造通知也能「占用」某个 notify_id 并留下记录；</li>
     *   <li>渠道随后用<b>同一个 notify_id</b> 发来<b>真正的</b>通知时，
     *       会被判定为「重复通知」直接返回 success —— <b>我们没处理，但渠道以为处理了</b>。
     *       这笔钱就收不到了，而且渠道不会再重试。这是会丢单的严重缺陷。</li>
     * </ol>
     * 修复办法有两条，本项目两条都做了：
     * <ul>
     *   <li><b>先验签</b>，验签不过的一律不进入业务分支；</li>
     *   <li>验签失败的记录用<b>派生 key</b>（{@code notifyId + ":INVALID"}）落库，
     *       这样它<b>不会占用渠道真正的 notify_id</b>，审计痕迹也保留了下来。</li>
     * </ul>
     */
    @Override
    public PayNotifyResult handleNotify(int channel, Map<String, String> params,
                                       String rawBody, String headers) {
        long start = System.currentTimeMillis();
        String notifyId = params.get("notify_id");
        String sign = params.get("sign");

        // ---- 1. 先验签。必须在任何落库/去重之前 ----
        boolean verified = gateway.verify(params, sign);

        // ---- 2. 留痕。验签失败时用派生 key，避免占用渠道的 notify_id ----
        PayNotifyLog logEntity = new PayNotifyLog();
        logEntity.setChannelNotifyId(verified ? notifyId : notifyId + INVALID_LOG_SUFFIX);
        logEntity.setChannel(channel);
        logEntity.setNotifyType(NOTIFY_TYPE_PAY);
        logEntity.setPaymentNo(params.get(PaymentNotifyHandler.FIELD_OUT_TRADE_NO));
        // order_no 是我们自己的字段（渠道原样回传），方便按订单号排查
        logEntity.setOrderNo(params.get("order_no"));
        logEntity.setTradeNo(params.get(PaymentNotifyHandler.FIELD_TRADE_NO));
        logEntity.setRequestBody(rawBody);
        logEntity.setRequestHeaders(headers);
        logEntity.setSign(sign);
        logEntity.setTraceId(MDC.get("traceId"));
        Long logId = notifyLogRecorder.record(logEntity);

        if (!verified) {
            long cost = System.currentTimeMillis() - start;
            notifyLogRecorder.finish(logId, PayNotifyLogRecorder.VERIFY_FAIL,
                    PayNotifyLogRecorder.HANDLE_FAIL, "验签失败", cost);
            log.warn("支付回调验签失败，已拒绝 | channel={} | notifyId={} | paymentNo={}",
                    channel, notifyId, params.get(PaymentNotifyHandler.FIELD_OUT_TRADE_NO));
            return PayNotifyResult.fail("验签失败");
        }

        if (logId == null) {
            // 验签通过的、同一条通知（同渠道 + 同 notify_id）已经收到过。
            // 直接返回成功让渠道停止重试 —— 不重复处理，也不报错。
            return PayNotifyResult.duplicate("同一条通知已收到过（notify_id 重复）");
        }

        // ---- 3. 业务处理（独立事务） ----
        try {
            PayNotifyResult result = notifyHandler.handle(channel, params, rawBody);
            int handleStatus = result.duplicate() ? PayNotifyLogRecorder.HANDLE_DUPLICATE
                    : (result.success() ? PayNotifyLogRecorder.HANDLE_SUCCESS
                    : PayNotifyLogRecorder.HANDLE_FAIL);
            notifyLogRecorder.finish(logId, PayNotifyLogRecorder.VERIFY_PASS, handleStatus,
                    result.message(), System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            // 业务异常同样要留痕，并且【返回 failure 而不是抛出去】——
            // 这样渠道会按协议重试，而不是收到 500 后做出不可预期的反应
            notifyLogRecorder.finish(logId, PayNotifyLogRecorder.VERIFY_PASS,
                    PayNotifyLogRecorder.HANDLE_FAIL, "处理异常：" + e.getMessage(),
                    System.currentTimeMillis() - start);
            log.error("支付回调处理异常 | channel={} | notifyId={}", channel, notifyId, e);
            return PayNotifyResult.fail("处理异常：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closePendingPayments(Long orderId, String reason) {
        // 逐条关闭而不是一条批量 SQL：支付单数量极少，
        // 而逐条能保证每条都经过 closePayment 的状态条件（只关「待支付」的，
        // 绝不会把已经支付成功的单关掉）
        var pending = paymentMapper.selectList(Wrappers.<PayPayment>lambdaQuery()
                .eq(PayPayment::getOrderId, orderId)
                .eq(PayPayment::getStatus, PaymentStatus.PENDING.getCode()));
        for (PayPayment payment : pending) {
            paymentMapper.closePayment(payment.getPaymentNo(), reason);
        }
        if (!pending.isEmpty()) {
            log.info("已关闭 {} 张待支付单 | orderId={} | 原因={}", pending.size(), orderId, reason);
        }
    }

    private int normalizeChannel(Integer channel) {
        if (channel == null) {
            return CHANNEL_MOCK_ALIPAY;
        }
        if (channel != CHANNEL_MOCK_ALIPAY && channel != CHANNEL_MOCK_WECHAT) {
            throw new BizException(ResultCode.PAY_CHANNEL_UNSUPPORTED, "渠道值：" + channel);
        }
        return channel;
    }

    private String generatePaymentNo() {
        return "PAY" + LocalDateTime.now().format(NO_DATE) + IdWorker.getId();
    }

    private static PaymentVO toVO(PayPayment payment) {
        PaymentVO vo = new PaymentVO();
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setOrderNo(payment.getOrderNo());
        vo.setAmount(payment.getAmount());
        vo.setPaidAmount(payment.getPaidAmount());
        vo.setChannel(payment.getChannel());
        vo.setStatus(payment.getStatus());
        vo.setTradeNo(payment.getTradeNo());
        vo.setSubject(payment.getSubject());
        vo.setExpireTime(payment.getExpireTime());
        vo.setPayTime(payment.getPayTime());
        vo.setErrorMsg(payment.getErrorMsg());
        try {
            vo.setStatusDesc(PaymentStatus.of(payment.getStatus()).getDesc());
        } catch (IllegalArgumentException e) {
            vo.setStatusDesc("未知");
        }
        // 真实接入时这里会是渠道返回的支付链接或二维码内容；
        // 本项目给一个模拟收银台的地址，点一下就等于「在渠道侧完成付款」
        vo.setCashierUrl("/api/pay/mock-gateway/" + payment.getPaymentNo() + "/pay");
        vo.setMockPayPath("/api/pay/mock-gateway/" + payment.getPaymentNo() + "/pay");
        return vo;
    }
}
