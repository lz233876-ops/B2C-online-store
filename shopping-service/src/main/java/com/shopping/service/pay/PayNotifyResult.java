package com.shopping.service.pay;

/**
 * 回调处理结果。
 *
 * @param success    业务是否处理成功（决定回调接口返回 success 还是 failure）
 * @param duplicate  是否是「重复通知」——已处理过，无需再处理，但仍返回成功让渠道停止重试
 * @param needRefund 是否需要原路退款（订单已关闭但钱到了）
 * @param message    说明，会写进 {@code pay_notify_log.handle_msg}
 */
public record PayNotifyResult(boolean success, boolean duplicate, boolean needRefund, String message) {

    public static PayNotifyResult ok(String message) {
        return new PayNotifyResult(true, false, false, message);
    }

    public static PayNotifyResult duplicate(String message) {
        return new PayNotifyResult(true, true, false, message);
    }

    /** 处理失败：返回 failure，渠道会按自己的策略重试 */
    public static PayNotifyResult fail(String message) {
        return new PayNotifyResult(false, false, false, message);
    }

    /** 钱收了但订单已关闭 → 需要退款。这是「成功」的（钱确实收到了），但要人工/自动退款 */
    public static PayNotifyResult needRefund(String message) {
        return new PayNotifyResult(true, false, true, message);
    }
}
