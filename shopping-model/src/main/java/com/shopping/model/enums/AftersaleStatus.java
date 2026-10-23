package com.shopping.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 售后单状态。
 * <pre>
 *   待审核(0) ──同意──> 仅退款：已同意(1) ──退款──> 退款完成(5)
 *      │                    └─ 退货退款：退货中(3) ──买家寄回──> 已收货(4) ──退款──> 退款完成(5)
 *      ├──拒绝──> 已拒绝(2)
 *      └──超时/用户撤销──> 已关闭(6)
 * </pre>
 * <p>
 * 注意「已同意(1)」只作为<b>仅退款</b>的中间态；退货退款从「待审核」直接进「退货中(3)」，
 * 因为对它来说"同意"之后的下一步动作是等买家寄货，而不是等退款。
 * 把这两条路径分开表达，比用一个笼统的「进行中」状态清晰得多。
 */
@Getter
@RequiredArgsConstructor
public enum AftersaleStatus {

    PENDING_AUDIT(0, "待审核"),
    APPROVED(1, "已同意"),
    REJECTED(2, "已拒绝"),
    RETURNING(3, "退货中"),
    RECEIVED(4, "已收货"),
    REFUNDED(5, "退款完成"),
    CLOSED(6, "已关闭");

    private final int code;
    private final String desc;

    public static AftersaleStatus of(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知售后状态：" + code));
    }

    /** 是否已经结束（终态，不能再被审核/收退货/退款） */
    public boolean isFinal() {
        return this == REJECTED || this == REFUNDED || this == CLOSED;
    }
}
