package com.shopping.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态与状态机。
 * <p>
 * <b>为什么要把「允许的流转」写成代码，而不是散落在各个方法里的 if：</b>
 * 订单状态是最容易出错的地方——少写一个判断就会出现「已取消的订单被发货」
 * 或者「已完成的订单又变成待付款」这类脏数据。<b>把合法的流转集中定义一次</b>，
 * 任何地方改状态都必须经过这道校验。
 * <pre>
 * 待付款(0) ──支付成功──> 待发货(1) ──发货──> 待收货(2) ──确认收货──> 已完成(3)
 *    │                                              │
 *    └──取消/超时──> 已取消(4)                       └──申请售后──> 售后中(5) ──> 已退款(6)
 * </pre>
 * <p>
 * ⚠️ <b>但仅有这个枚举还不够。</b>它只能挡住「单线程下明显的非法流转」；
 * 真正的并发保护必须落在数据库上：
 * {@code UPDATE oms_order SET status = 新 WHERE id = ? AND status = 期望旧值}。
 * 受影响行数为 0 就说明状态已经被别人改了——那时必须抛异常，而不是继续往下走。
 * <b>枚举负责「业务规则」，条件更新负责「并发正确性」，两者缺一不可。</b>
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    /** 待付款：下单成功、尚未支付。可支付、可取消、可超时关闭 */
    PENDING_PAYMENT(0, "待付款"),
    /** 待发货：已支付，等商家发货 */
    PENDING_DELIVERY(1, "待发货"),
    /** 待收货：商家已发货 */
    PENDING_RECEIPT(2, "待收货"),
    /** 已完成：用户确认收货。可申请售后 */
    COMPLETED(3, "已完成"),
    /** 已取消：用户主动取消、超时未支付、或商家取消 */
    CANCELLED(4, "已取消"),
    /** 售后中：已申请退款/退货，等待处理 */
    AFTER_SALE(5, "售后中"),
    /** 已退款：售后退款完成（终态） */
    REFUNDED(6, "已退款");

    private final int code;
    private final String desc;

    /**
     * 允许的流转表。
     * <p>注意几个刻意的限制：
     * <ul>
     *   <li>{@code 待发货} 也能转 {@code 已取消} —— 商家发现没货时可以取消并退款；</li>
     *   <li>{@code 已取消} 是<b>终态</b>，不能回到任何状态；</li>
     *   <li>只有 {@code 待收货} 和 {@code 已完成} 能进售后 —— 没发货就退款走的是「取消订单」，
     *       不是售后流程（否则同一个业务会有两条路径，库存回滚逻辑会重复）。</li>
     * </ul>
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING_PAYMENT, Set.of(PENDING_DELIVERY, CANCELLED),
            PENDING_DELIVERY, Set.of(PENDING_RECEIPT, CANCELLED),
            PENDING_RECEIPT, Set.of(COMPLETED, AFTER_SALE),
            COMPLETED, Set.of(AFTER_SALE),
            // 售后中：要么退款完成（终态），要么「审核被拒 / 用户撤销」→ 回到售后前的状态。
            // ⚠️ 回到哪个状态是【推导】出来的，不需要额外字段：
            //    receive_time 为空 → 说明用户还没确认收货 → 原状态是「待收货」
            //    receive_time 非空 → 原状态是「已完成」
            //    这样避免了「在订单表里再存一个 pre_aftersale_status」这种冗余且容易写错的字段。
            AFTER_SALE, Set.of(REFUNDED, PENDING_RECEIPT, COMPLETED),
            CANCELLED, Set.of(),
            REFUNDED, Set.of()
    );

    public static OrderStatus of(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知订单状态：" + code));
    }

    /** 是否可以从当前状态流转到目标状态 */
    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** 是否终态（不可再流转） */
    public boolean isFinal() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).isEmpty();
    }

    /** 是否已经占用/消耗了库存（用于判断取消时要不要回滚库存） */
    public boolean hasStockLocked() {
        // 待付款：库存已锁定（未扣减）；待发货/待收货/已完成：库存已实扣。
        // 已取消/已退款：库存已释放或回补。售后中：库存已实扣（要等退款才回补）。
        return this == PENDING_PAYMENT || this == PENDING_DELIVERY
                || this == PENDING_RECEIPT || this == COMPLETED || this == AFTER_SALE;
    }
}
