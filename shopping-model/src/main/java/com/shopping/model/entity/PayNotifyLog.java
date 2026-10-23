package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 支付回调日志（{@code pay_notify_log}）。
 * <p>
 * <b>对外回调必须「先留痕、再处理」，而且留痕要独立事务。</b>
 * 理由是排查线上问题的第一手材料：用户说「我付了钱但订单没变」，
 * 你需要的恰恰是<b>那条处理失败的回调记录</b>。
 * 如果日志和业务在同一个事务里，业务一失败日志就被一起回滚 ——
 * 最需要它的时候它正好不在。<b>这是回调日志最容易做错的地方。</b>
 * <p>
 * 唯一索引 {@code uk_channel_channel_notify_id(channel, channel_notify_id)}
 * 只能挡住「同一条通知被原样重复投递」。
 * ⚠️ 真实渠道重试时<b>常常换一个 notify_id</b>（支付宝就是这样），
 * 所以它<b>不能</b>作为业务幂等的唯一依据 ——
 * 业务幂等必须落在「支付单状态的条件更新」上，见 {@code PaymentService}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_notify_log")
public class PayNotifyLog extends BaseEntity {

    /** 渠道通知ID（渠道生成，同一渠道内唯一） */
    private String channelNotifyId;

    /** 渠道：1 模拟支付宝，2 模拟微信 */
    private Integer channel;

    /** 通知类型：1 支付结果，2 退款结果 */
    private Integer notifyType;

    /** 商户支付单号 */
    private String paymentNo;

    /** 订单号 */
    private String orderNo;

    /** 渠道流水号 */
    private String tradeNo;

    /** 原始请求体（排查时按这个复现） */
    private String requestBody;

    /** 原始请求头 */
    private String requestHeaders;

    /** 渠道签名 */
    private String sign;

    /** 验签结果：0 未验签，1 通过，2 失败 */
    private Integer verifyStatus;

    /** 处理结果：0 待处理，1 成功，2 失败，3 重复通知已忽略 */
    private Integer handleStatus;

    /** 处理说明 */
    private String handleMsg;

    /** 处理完成时间 */
    private LocalDateTime handleTime;

    /** 处理耗时（毫秒）—— 回调处理太慢会导致渠道重复投递，值得监控 */
    private Long costTime;

    /** 链路追踪ID */
    private String traceId;
}
