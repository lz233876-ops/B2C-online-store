package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 售后单（{@code oms_aftersale}）。
 * <p>
 * <b>售后挂在「订单明细」上，而不是整笔订单上。</b>因为一笔订单可能买 3 件商品，
 * 用户只想退其中 1 件。挂在订单上会导致「退一件就得退整单」这种无法表达的业务。
 * 唯一索引 {@code uk_order_item_id_type(order_item_id, type, deleted)} 保证
 * 「同一个明细、同一种售后类型」只能提交一次。
 * <p>
 * <b>为什么售后要独立一张表而不是在订单上打个标记：</b>
 * 售后有自己完整的状态机（待审核 → 同意/拒绝 → 退货中 → 已收货 → 退款完成/已关闭）、
 * 自己的审核人与审核意见、自己的物流信息与超时规则。
 * 塞进订单表会把订单状态机污染成两套状态纠缠在一起。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oms_aftersale")
public class OmsAftersale extends BaseEntity {

    /** 商家ID */
    private Long merchantId;

    /** 售后单号，如 AS20261018120000001 */
    private String aftersaleNo;

    /** 订单ID */
    private Long orderId;

    /** 订单号（冗余） */
    private String orderNo;

    /** 订单明细ID —— 售后是针对【某个商品】的，不是整单 */
    private Long orderItemId;

    /** 申请会员ID */
    private Long userId;

    /** 售后类型，见 {@link com.shopping.model.enums.AftersaleType} */
    private Integer type;

    /** 售后状态，见 {@link com.shopping.model.enums.AftersaleStatus} */
    private Integer status;

    /** 申请原因 */
    private String reason;

    /** 问题描述 */
    private String description;

    /** 凭证图片 URL 数组（JSON） */
    private String images;

    /** 申请退款金额（分） */
    private Long refundAmount;

    /** 实际退款金额（分）—— 审核时可能改价，所以与申请金额分开 */
    private Long actualRefundAmount;

    /** 退货数量 */
    private Integer quantity;

    /** 审核人ID */
    private Long auditUserId;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核意见 */
    private String auditRemark;

    /** 退货物流公司 */
    private String returnCompany;

    /** 退货物流单号 */
    private String returnNo;

    /** 买家寄回时间 */
    private LocalDateTime returnTime;

    /** 商家确认收货时间 */
    private LocalDateTime receiveTime;

    /** 售后完成时间 */
    private LocalDateTime finishTime;

    /** 超时未处理自动关闭时间（7 天） */
    private LocalDateTime autoCloseTime;
}
