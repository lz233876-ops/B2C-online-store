package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 支付单（{@code pay_payment}）。
 * <p>
 * <b>为什么要独立于订单存在：</b>支付是「与外部渠道打交道」的过程，
 * 它会失败、会重试、会超时、会换渠道重试。把这些过程状态塞进订单表，
 * 订单状态机会被污染成一团乱麻。<b>一个订单可以有多个支付单</b>，
 * 但同一时刻同一个 (订单, 渠道) 只能有一个「待支付」的支付单
 * —— 这一点由唯一索引 {@code uk_order_id_channel_status} 保证。
 * <p>
 * <b>{@code payment_no} 是给渠道看的商户订单号</b>（对应支付宝的 {@code out_trade_no}），
 * {@code trade_no} 是渠道返回的流水号（对应 {@code trade_no}）。
 * 退款、对账都以这两个号为锚点。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_payment")
public class PayPayment extends BaseEntity {

    /** 商家ID */
    private Long merchantId;

    /** 商户支付单号（给渠道看的 out_trade_no），全局唯一 */
    private String paymentNo;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 会员ID */
    private Long userId;

    /** 应付金额（分）。回调时用它校验渠道报的金额，防止「1 分钱买手机」 */
    private Long amount;

    /** 实付金额（分） */
    private Long paidAmount;

    /** 渠道：1 模拟支付宝，2 模拟微信 */
    private Integer channel;

    /** 状态，见 {@link com.shopping.model.enums.PaymentStatus} */
    private Integer status;

    /** 渠道流水号（渠道返回，退款时要用） */
    private String tradeNo;

    /** 付款方账号 */
    private String buyerAccount;

    /** 支付标题（展示在收银台） */
    private String subject;

    /** 渠道回调的原始数据（留存排查用） */
    private String notifyData;

    /** 失败原因 */
    private String errorMsg;

    /** 支付单过期时间（超过后不能再支付） */
    private LocalDateTime expireTime;

    /** 支付成功时间 */
    private LocalDateTime payTime;

    /** 关闭时间 */
    private LocalDateTime closeTime;

    /** 乐观锁版本号 */
    private Integer version;
}
