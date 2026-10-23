package com.shopping.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额工具。
 * <p>
 * <b>全项目约定：金额在数据库与 Java 中一律用 {@code long} 存「分」</b>，
 * 绝不用 {@code double}（存在浮点精度误差，如 {@code 0.1 + 0.2 != 0.3}）。
 * 只有在<b>展示</b>与<b>对接外部渠道</b>时才转成「元」。
 * <p>
 * 面试要点：为什么不用 {@code decimal}？{@code decimal} 同样精确，但 Java 侧要用
 * {@code BigDecimal}，运算与序列化都更繁琐，且不同数据库实现精度有差异。
 * 存「分」用 {@code long} 是电商最常见的做法，跨语言也不会出问题。
 * 代价是<b>前端必须显式除以 100 展示</b>——所以本类提供了格式化方法。
 */
public final class MoneyUtils {

    private MoneyUtils() {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * 分 → 元字符串（保留两位小数）。
     * 用于展示，如 {@code 12345 -> "123.45"}。
     */
    public static String fenToYuan(Long fen) {
        if (fen == null) {
            return "0.00";
        }
        return BigDecimal.valueOf(fen)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /**
     * 分 → 元（BigDecimal，保留两位）。
     * 用于对接外部渠道时构造金额参数。
     */
    public static BigDecimal fenToYuanDecimal(Long fen) {
        if (fen == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(fen).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /**
     * 元 → 分。
     * 用 {@code BigDecimal} 解析再乘 100，避免用 {@code Double.parseDouble} 引入误差。
     *
     * @throws IllegalArgumentException 入参不是合法数字
     */
    public static long yuanToFen(String yuan) {
        if (yuan == null || yuan.isBlank()) {
            return 0L;
        }
        return new BigDecimal(yuan.trim())
                .multiply(HUNDRED)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static long yuanToFen(BigDecimal yuan) {
        if (yuan == null) {
            return 0L;
        }
        return yuan.multiply(HUNDRED)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * 计算「单价 × 数量」的总价，防溢出。
     * 商品单价与数量都是可控的，但用 {@code Math.multiplyExact} 显式暴露溢出，
     * 好过静默产生一个负数金额。
     */
    public static long multiply(long unitPriceFen, long quantity) {
        return Math.multiplyExact(unitPriceFen, quantity);
    }

    /**
     * 计算应付金额 = 商品总额 + 运费 - 优惠。
     * <p>
     * <b>不允许为负</b>：优惠券叠加或人工改价可能把应付算成负数，
     * 那等于平台倒贴钱，必须兜底为 0。
     */
    public static long payable(long goodsAmount, long freight, long discount) {
        long payable = goodsAmount + freight - discount;
        return Math.max(payable, 0L);
    }
}
