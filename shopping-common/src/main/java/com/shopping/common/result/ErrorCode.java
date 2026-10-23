package com.shopping.common.result;

/**
 * 错误码契约。
 * <p>
 * 抽成接口而不是直接用一个枚举，是为了让各业务模块能定义自己的错误码
 * （如 {@code OrderErrorCode}、{@code SeckillErrorCode}），同时全局异常处理器
 * 只需要面向这一个接口，不必知道具体枚举。
 */
public interface ErrorCode {

    /** 业务状态码 */
    int getCode();

    /** 面向用户的提示信息 */
    String getMessage();
}
