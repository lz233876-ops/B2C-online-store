package com.shopping.service.pay;

import java.util.Map;

/**
 * 支付服务。
 * <p>
 * <b>本模块的设计目标不是「让支付成功」，而是把真实渠道的麻烦之处如实处理掉：</b>
 * 异步通知、签名、重复投递、金额篡改、钱到了但订单已关闭。
 * 「点一下就成功」的假支付在面试里讲不出任何东西。
 */
public interface PaymentService {

    /**
     * 为订单创建支付单（同一个「订单 + 渠道 + 待支付」只会有一条，见唯一索引
     * {@code uk_order_id_channel_status}），返回模拟收银台所需的信息。
     */
    com.shopping.model.vo.PaymentVO createPayment(String orderNo, Integer channel);

    /** 查询某订单的支付单（当前会员的订单） */
    com.shopping.model.vo.PaymentVO queryByOrderNo(String orderNo);

    /**
     * 处理渠道回调。
     * <p><b>本方法不做业务事务</b>，只负责编排「留痕 → 验签 → 交业务处理 → 回写日志」。
     * 业务部分在 {@link PaymentNotifyHandler} 里，独立事务 ——
     * 这样业务回滚时，回调日志（独立事务）才能留下来。
     *
     * @param channel 渠道
     * @param params  回调参数（含 sign）
     * @param rawBody 原始请求体，留档排查用
     * @param headers 原始请求头，留档排查用
     */
    PayNotifyResult handleNotify(int channel, Map<String, String> params, String rawBody, String headers);

    /** 关闭某订单下所有「待支付」的支付单（订单取消 / 超时关单时调用） */
    void closePendingPayments(Long orderId, String reason);
}
