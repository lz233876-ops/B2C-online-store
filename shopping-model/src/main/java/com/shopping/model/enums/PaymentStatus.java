package com.shopping.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 支付单状态。
 * <p>
 * <b>支付单与订单是两张独立的表、两套独立的状态</b>，这是刻意的：
 * <ul>
 *   <li>一个订单<b>可以有多个支付单</b>（第一次支付失败/超时关闭后重新发起）；</li>
 *   <li>支付单由<b>支付渠道</b>驱动状态（回调），订单由<b>业务</b>驱动状态。</li>
 * </ul>
 * 如果把它们合成一个状态，就会出现「支付渠道说成功了，但业务上这单已经被取消」
 * 这种无法表达的情况——而它恰恰是真实世界最常见的一种。
 */
@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    /** 待支付：已创建，等待渠道回调 */
    PENDING(0, "待支付"),
    /** 支付成功：终态 */
    SUCCESS(1, "支付成功"),
    /** 支付失败：可重新发起 */
    FAILED(2, "支付失败"),
    /** 已关闭：超时或订单被取消 */
    CLOSED(3, "已关闭"),
    /** 部分退款 */
    PARTIAL_REFUNDED(4, "部分退款"),
    /** 全额退款：终态 */
    REFUNDED(5, "全额退款");

    private final int code;
    private final String desc;

    public static PaymentStatus of(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知支付状态：" + code));
    }
}
