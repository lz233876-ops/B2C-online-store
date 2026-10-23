package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 秒杀成功记录（{@code sms_seckill_record}）。
 * <p>
 * <b>这张表是「抢到了」的唯一凭据，也是三道幂等闸门所在：</b>
 * <table border="1">
 *   <tr><th>唯一索引</th><th>防的是什么</th></tr>
 *   <tr><td>{@code uk_activity_id_user_id}</td><td><b>一人一单</b>（同一活动同一用户只能抢到一次）</td></tr>
 *   <tr><td>{@code uk_request_id}</td><td><b>接口级幂等</b>（用户狂点按钮 / MQ 重复投递）</td></tr>
 *   <tr><td>{@code uk_order_no}</td><td>一个订单号只能对应一条记录</td></tr>
 * </table>
 * <p>
 * {@code order_id} / {@code order_no} <b>允许为空</b>：秒杀是「先抢到、后异步建单」，
 * 记录先落库（占住名额）再由消费者补上订单号。前端靠轮询查询结果。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sms_seckill_record")
public class SmsSeckillRecord extends BaseEntity {

    private Long merchantId;
    private Long activityId;
    private Long seckillSkuId;
    private Long skuId;
    private Long userId;
    /** 抢购数量（本项目恒为 1） */
    private Integer quantity;
    /** 秒杀成交价快照（分） */
    private Long seckillPrice;
    /** 生成的订单ID（异步创建，可能短暂为空） */
    private Long orderId;
    /** 生成的订单号（异步创建，可能短暂为空） */
    private String orderNo;
    /** 0 已抢到待支付，1 已支付，2 已取消/超时，3 已退款 */
    private Integer status;
    /** 客户端请求唯一ID（接口级幂等） */
    private String requestId;
    /** 抢购 IP（风控用） */
    private String ip;
}
