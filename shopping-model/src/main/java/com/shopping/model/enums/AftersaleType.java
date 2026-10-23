package com.shopping.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 售后类型。
 * <p>
 * <b>最重要的区别是「货有没有回来」</b>，它决定了退款后要不要回补库存：
 * <table border="1">
 *   <tr><th>类型</th><th>货在哪</th><th>退钱</th><th>回补库存</th></tr>
 *   <tr><td>仅退款</td><td>还在买家手里</td><td>是</td><td><b>否</b></td></tr>
 *   <tr><td>退货退款</td><td>寄回商家</td><td>是</td><td><b>是</b></td></tr>
 * </table>
 * <p>
 * ⚠️ <b>「仅退款要不要回补库存」是最容易写错的地方。</b>钱退了、货没回来，
 * 如果还去加库存，就等于凭空多出一件货——账面库存虚高，最终会超卖。
 * 这个判断必须收敛在枚举里（{@link #refundsStock()}），而不是散落在业务代码的 if 中。
 */
@Getter
@RequiredArgsConstructor
public enum AftersaleType {

    /** 仅退款：货不退回，只退钱。**不回补库存** */
    REFUND_ONLY(1, "仅退款", false, false),
    /** 退货退款：买家寄回商品，商家收到后退钱。**回补库存** */
    RETURN_REFUND(2, "退货退款", true, true),
    /** 换货：寄回后换新，不涉及退款。本项目暂不支持 */
    EXCHANGE(3, "换货", true, false);

    private final int code;
    private final String desc;
    /** 是否需要买家把货寄回来 */
    private final boolean needsReturn;
    /** 退款完成后是否需要回补库存 */
    private final boolean refundsStock;

    /**
     * ⚠️ 这两个方法<b>显式声明</b>，没有依赖 Lombok 的 {@code @Getter}。
     * 因为 Lombok 对 {@code boolean} 字段生成的是 {@code isNeedsReturn()} / {@code isRefundsStock()}，
     * 而不是 {@code needsReturn()} / {@code refundsStock()}。
     * 业务里读起来「是否要寄回」「是否回补库存」这种判断，写成 {@code type.needsReturn()}
     * 比 {@code type.isNeedsReturn()} 更自然，也更接近领域语言 —— 所以手写。
     */
    public boolean needsReturn() {
        return needsReturn;
    }

    public boolean refundsStock() {
        return refundsStock;
    }

    public static AftersaleType of(int code) {
        return Arrays.stream(values())
                .filter(t -> t.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知售后类型：" + code));
    }
}
