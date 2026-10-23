package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单明细（{@code oms_order_item}）。
 * <p>
 * <b>本表是「商品信息快照」的典型场景</b>：{@code spu_name} / {@code sku_name} /
 * {@code sku_image} / {@code spec_desc} / {@code sku_code} / {@code price} 全部是下单那一刻的值。
 * 商品后来改名、改价、换图、甚至被删除，历史订单展示的仍然必须是当时的样子——
 * 这是电商的合规要求，也是客诉时唯一的凭据。
 * <p>
 * 所以：<b>订单明细绝不去 JOIN 商品表取名字和价格</b>，一律读自己的快照列。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oms_order_item")
public class OmsOrderItem extends BaseEntity {

    /** 商家ID */
    private Long merchantId;

    /** 订单ID */
    private Long orderId;

    /** 订单号（冗余，方便按订单号直接查明细而不必先查主表） */
    private String orderNo;

    /** 会员ID（冗余，用于「我的订单」按用户过滤时避免回表） */
    private Long userId;

    /** SPU ID */
    private Long spuId;

    /** SKU ID */
    private Long skuId;

    /** 商品名称快照 */
    private String spuName;

    /** SKU 名称快照 */
    private String skuName;

    /** 商品图片快照 */
    private String skuImage;

    /** 规格摘要快照，如「128GB / 黑色」 */
    private String specDesc;

    /** SKU 编码快照 */
    private String skuCode;

    /** 成交单价快照（分） */
    private Long price;

    /** 数量 */
    private Integer quantity;

    /** 小计（分）= price × quantity */
    private Long totalAmount;

    /** 本项分摊的优惠（分） */
    private Long discountAmount;

    /** 本项实付（分） */
    private Long payAmount;

    /** 退款状态：0 未退款，1 部分退款，2 全额退款 */
    private Integer refundStatus;

    /** 已退款金额（分） */
    private Long refundAmount;

    /** 是否已评价：0 否，1 是 */
    private Integer isCommented;
}
