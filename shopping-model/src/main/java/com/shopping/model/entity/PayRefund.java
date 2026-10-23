package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 退款单（{@code pay_refund}）。
 * <p>
 * <b>退款单与售后单是两回事：</b>售后是「用户提出的诉求」，退款是「钱真的退回去了」。
 * 一个售后可能对应 0 个退款单（审核被拒）或 1 个退款单（同意后实际退款）。
 * 分开之后，退款这个「与外部渠道打交道的动作」可以独立重试、独立记录失败原因
 * （{@code retry_count} / {@code next_retry_time}），而不会把售后单的状态搅乱。
 * <p>
 * {@code uk_aftersale_id(aftersale_id, deleted)} 保证<b>一个售后只会产生一次退款</b>——
 * 这是退款幂等的最后一道闸门：即便上层因为重试重复调用了退款，数据库也会挡住。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_refund")
public class PayRefund extends BaseEntity {

    /** 商家ID */
    private Long merchantId;

    /** 退款单号，全局唯一 */
    private String refundNo;

    /** 关联的支付单号（退款是退到原来那笔支付上） */
    private String paymentNo;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 售后单ID */
    private Long aftersaleId;

    /** 会员ID */
    private Long userId;

    /** 退款渠道：1 模拟支付宝，2 模拟微信，3 余额 */
    private Integer channel;

    /** 退款金额（分） */
    private Long refundAmount;

    /** 退款类型：1 全额，2 部分 */
    private Integer refundType;

    /** 退款状态：0 待退款，1 退款中，2 退款成功，3 退款失败 */
    private Integer status;

    /** 渠道退款单号 */
    private String channelRefundNo;

    /** 退款原因 */
    private String reason;

    /** 失败原因 */
    private String errorMsg;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;

    /** 退款成功时间 */
    private LocalDateTime refundTime;

    /** 乐观锁版本号 */
    private Integer version;
}
