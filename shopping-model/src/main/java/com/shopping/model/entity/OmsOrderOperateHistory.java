package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单操作流水（{@code oms_order_operate_history}）。
 * <p>
 * <b>每一次状态流转都必须写一条</b>，理由和库存流水一样：
 * 订单出问题时（用户说「我没取消啊」、商家说「我发了货」），
 * 只有流水能还原「谁、什么时候、把订单从什么状态改成了什么状态」。
 * <p>
 * {@code from_status} 默认 -1 表示「创建订单」这个动作没有前置状态。
 * {@code trace_id} 打通链路追踪：一次请求涉及订单、库存、支付多条流水，
 * 用同一个 traceId 串起来才能在日志里还原完整时序。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oms_order_operate_history")
public class OmsOrderOperateHistory extends BaseEntity {

    /** 商家ID */
    private Long merchantId;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 动作：CREATE / PAY / DELIVER / RECEIVE / CANCEL / TIMEOUT_CLOSE / APPLY_AFTER_SALE / REFUND */
    private String action;

    /** 变更前状态，-1 表示无（创建） */
    private Integer fromStatus;

    /** 变更后状态 */
    private Integer toStatus;

    /** 操作人类型：1 用户，2 管理员，3 系统 */
    private Integer operatorType;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 备注 */
    private String note;

    /** 链路追踪ID */
    private String traceId;
}
