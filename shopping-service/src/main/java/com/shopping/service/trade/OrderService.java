package com.shopping.service.trade;

import com.shopping.common.result.PageResult;
import com.shopping.model.dto.OrderSubmitDTO;
import com.shopping.model.vo.OrderDetailVO;
import com.shopping.model.vo.OrderSubmitVO;
import com.shopping.model.vo.OrderVO;

/**
 * 订单服务。
 * <p>
 * <b>下单是整个项目里最长的一个事务</b>，也是唯一一个同时动
 * 购物车、库存、订单三张表的事务。它的正确性依赖两点：
 * <ol>
 *   <li><b>原子性</b>：锁库存、写订单、清购物车必须在同一个事务里。
 *       任何一步失败都必须整体回滚——特别是库存锁定，否则会出现
 *       「库存被锁了但没有对应的订单」，那部分库存就永久消失了（比超卖更难查）；</li>
 *   <li><b>条件更新</b>：所有状态流转都带 {@code WHERE status = 期望值}，
 *       受影响行数为 0 就抛异常，绝不允许「先查后改」。</li>
 * </ol>
 */
public interface OrderService {

    /** 提交订单：校验地址 → 校验商品 → 锁库存 → 建订单 → 清购物车 */
    OrderSubmitVO submit(OrderSubmitDTO dto);

    /** 我的订单分页；{@code status} 为 null 表示全部 */
    PageResult<OrderVO> pageOfCurrentUser(int pageNum, int pageSize, Integer status);

    /** 我的订单详情（含明细与状态流水）；不属于当前会员时返回「订单不存在」 */
    OrderDetailVO detailOfCurrentUser(Long orderId);

    /** 用户取消订单（只能取消待付款的） */
    void cancel(Long orderId, String reason);

    /** 用户确认收货（待收货 → 已完成） */
    void confirmReceive(Long orderId);

    /* ---------------- 管理端 ---------------- */

    PageResult<OrderVO> adminPage(int pageNum, int pageSize, Integer status, String orderNo);

    OrderDetailVO adminDetail(Long orderId);

    /** 发货（待发货 → 待收货） */
    void deliver(Long orderId, String deliveryCompany, String deliveryNo);

    /**
     * 关闭<b>单个</b>超时未支付的订单（幂等）。
     * <p>独立成方法是为了让每笔订单有<b>自己的事务</b>：批量关闭时，
     * 一笔失败不能回滚掉其它已经成功的。<b>这是定时任务处理批数据的基本要求。</b>
     *
     * @return true 表示本次真的关闭了（false = 已支付/已取消，无需处理）
     */
    boolean closeTimeoutOrder(Long orderId);

    /** 扫描出已超时未支付的订单 ID（只查询，不改状态） */
    java.util.List<Long> findTimeoutOrderIds(int limit);
}
