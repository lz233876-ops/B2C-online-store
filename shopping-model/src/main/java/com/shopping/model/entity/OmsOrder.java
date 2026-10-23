package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订单主表（{@code oms_order}）。
 * <p>
 * <b>金额拆成 7 个字段而不是只存一个「应付金额」</b>，因为对账和客诉都需要知道钱是怎么算出来的：
 * <pre>
 *   total_amount            商品总额   = Σ(SKU 单价 × 数量)
 *   freight_amount          运费
 *   discount_amount         活动优惠（预留）
 *   coupon_amount           优惠券抵扣（Phase 3）
 *   points_amount           积分抵扣（预留）
 *   member_discount_amount  会员等级折扣（预留）
 *   ────────────────────────────────────────────────
 *   pay_amount              应付 = 商品总额 + 运费 - 各种抵扣（不允许为负）
 *   paid_amount             实付（支付回调后回填）
 * </pre>
 * <b>收货人信息是快照</b>（{@code receiver_*}）：用户改了默认地址、甚至删了地址，
 * 历史订单的收货信息也不能跟着变——否则发货时地址就错了。
 * <p>
 * <b>{@code version} 字段</b>：配合 MyBatis-Plus 的乐观锁做演示，
 * 但<b>订单状态流转实际用的是「条件更新」</b>（{@code WHERE status = 期望值}），
 * 因为它能同时表达「状态没变才允许改」这个业务语义，比版本号更直观。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oms_order")
public class OmsOrder extends BaseEntity {

    /** 商家ID，多商户预留 */
    private Long merchantId;

    /** 订单号，全局唯一。取消/逻辑删除后都不允许复用（唯一索引不带 deleted） */
    private String orderNo;

    /** 下单会员 ID */
    private Long userId;

    /** 订单类型：1 普通订单，2 秒杀订单（Phase 3） */
    private Integer orderType;

    /** 订单来源：1 PC，2 H5，3 小程序，4 App */
    private Integer sourceType;

    /** 商品总额（分） */
    private Long totalAmount;

    /** 运费（分） */
    private Long freightAmount;

    /** 活动优惠（分） */
    private Long discountAmount;

    /** 优惠券抵扣（分） */
    private Long couponAmount;

    /** 积分抵扣（分） */
    private Long pointsAmount;

    /** 会员等级折扣（分） */
    private Long memberDiscountAmount;

    /** 应付金额（分）= 商品总额 + 运费 - 各项抵扣，不允许为负 */
    private Long payAmount;

    /** 实付金额（分），支付成功后回填 */
    private Long paidAmount;

    /** 使用的积分数量 */
    private Integer usePoints;

    /** 本单获得的积分 */
    private Integer gainPoints;

    /** 本单获得的成长值 */
    private Integer gainGrowth;

    /** 支付方式：0 未支付，1 模拟支付宝，2 模拟微信，3 余额支付 */
    private Integer payType;

    /** 订单状态，见 {@link com.shopping.model.enums.OrderStatus} */
    private Integer status;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 发货时间 */
    private LocalDateTime deliveryTime;

    /** 收货时间 */
    private LocalDateTime receiveTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 关闭时间（取消或超时） */
    private LocalDateTime closeTime;

    /**
     * 自动关闭时间 = 下单时间 + 30 分钟。
     * <p><b>落地成字段而不是「下单时间 + 30min」实时算</b>，有两个好处：
     * ① 扫描超时订单时可以走索引 {@code WHERE status=0 AND auto_close_time < NOW()}，
     *    不用对 create_time 做函数运算（函数运算会让索引失效）；
     * ② 运营可以单独调整某个订单的关闭时间（比如大促延长），不用改代码。
     */
    private LocalDateTime autoCloseTime;

    /** 取消原因 */
    private String cancelReason;

    /** 物流公司 */
    private String deliveryCompany;

    /** 物流单号 */
    private String deliveryNo;

    /** 收货人姓名（<b>快照</b>） */
    private String receiverName;

    /** 收货人手机号（<b>快照</b>） */
    private String receiverPhone;

    /** 收货人省（<b>快照</b>） */
    private String receiverProvince;

    /** 收货人市（<b>快照</b>） */
    private String receiverCity;

    /** 收货人区县（<b>快照</b>） */
    private String receiverDistrict;

    /** 收货人详细地址（<b>快照</b>） */
    private String receiverDetailAddress;

    /** 用户备注 */
    private String note;

    /** 管理员备注 */
    private String adminNote;

    /** 乐观锁版本号（演示用，状态流转实际用条件更新） */
    private Integer version;
}
