package com.shopping.common.exception;

import com.shopping.common.result.ErrorCode;
import com.shopping.common.result.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。
 * <p>
 * 约定：
 * <ul>
 *   <li>可预期的业务失败（库存不足、订单状态已变）抛本异常，由全局异常处理器转成友好提示；</li>
 *   <li>不可预期的错误（空指针、SQL 异常）不要包装成本异常，让它们冒泡到兜底分支，
 *       因为那意味着有 bug，需要暴露出来而不是被当成正常业务失败吞掉。</li>
 * </ul>
 * <p>
 * 本异常<b>不需要</b>填充堆栈：业务异常是流程控制的一部分，频繁抛异常时
 * {@code fillInStackTrace} 的开销是可观的（秒杀场景尤其明显），因此重写为 no-op。
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage(), null, false, false);
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message, null, false, false);
        this.code = errorCode.getCode();
    }

    public BizException(String message) {
        super(message, null, false, false);
        this.code = ResultCode.INTERNAL_ERROR.getCode();
    }

    /* ---------------- 便捷静态方法 ---------------- */

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }

    /**
     * 断言为真，否则抛出业务异常。
     * 用法：{@code BizException.isTrue(stock >= num, ResultCode.STOCK_NOT_ENOUGH);}
     */
    public static void isTrue(boolean expression, ErrorCode errorCode) {
        if (!expression) {
            throw new BizException(errorCode);
        }
    }

    /**
     * 断言非空，否则抛出业务异常。
     */
    public static <T> T notNull(T target, ErrorCode errorCode) {
        if (target == null) {
            throw new BizException(errorCode);
        }
        return target;
    }
}
