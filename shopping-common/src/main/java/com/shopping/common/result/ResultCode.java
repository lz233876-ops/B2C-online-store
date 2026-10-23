package com.shopping.common.result;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 全局通用错误码。
 * <p>
 * 编码规范：
 * <ul>
 *   <li>{@code 2xx} 成功</li>
 *   <li>{@code 4xx / 5xx} 与 HTTP 语义对齐的通用错误</li>
 *   <li>{@code 1xxx} 认证授权</li>
 *   <li>{@code 2xxx} 商品与库存</li>
 *   <li>{@code 3xxx} 交易与订单</li>
 *   <li>{@code 4xxx} 支付</li>
 *   <li>{@code 5xxx} 营销与秒杀</li>
 * </ul>
 * 业务模块可以定义自己的错误码枚举实现 {@link ErrorCode}，不必都堆在这里。
 */
@Getter
@RequiredArgsConstructor
public enum ResultCode implements ErrorCode {

    /* ---------- 通用 ---------- */
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数有误"),
    UNAUTHORIZED(401, "登录已失效，请重新登录"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "数据已被修改，请刷新后重试"),
    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试"),

    /* ---------- 认证授权 1xxx ---------- */
    LOGIN_FAILED(1001, "用户名或密码错误"),
    ACCOUNT_DISABLED(1002, "账号已被禁用"),
    TOKEN_EXPIRED(1003, "登录已过期，请重新登录"),
    TOKEN_INVALID(1004, "登录凭证无效"),
    MEMBER_NOT_FOUND(1005, "会员不存在"),
    PHONE_ALREADY_REGISTERED(1006, "该手机号已注册，请直接登录"),
    MEMBER_DISABLED(1007, "会员账号已被禁用"),

    /* ---------- 商品与库存 2xxx ---------- */
    PRODUCT_NOT_FOUND(2001, "商品不存在或已下架"),
    SKU_NOT_FOUND(2002, "所选规格不存在或已下架"),
    STOCK_NOT_ENOUGH(2003, "库存不足"),
    STOCK_DEDUCT_FAILED(2004, "库存扣减失败，请重试"),

    /* ---------- 商品中心 · 类目 21xx ---------- */
    CATEGORY_NOT_FOUND(2101, "类目不存在"),
    CATEGORY_HAS_CHILDREN(2102, "该类目下还有子类目，不能删除"),
    CATEGORY_HAS_PRODUCT(2103, "该类目下还有商品，不能删除"),
    CATEGORY_LEVEL_EXCEED(2104, "类目层级超出上限（最多三级）"),
    CATEGORY_PARENT_INVALID(2105, "父类目无效，不能挂到自己的子类目下"),
    CATEGORY_NAME_DUPLICATE(2106, "同级下已存在同名类目"),

    /* ---------- 商品中心 · 品牌 22xx ---------- */
    BRAND_NOT_FOUND(2201, "品牌不存在"),
    BRAND_NAME_DUPLICATE(2202, "品牌名称已存在"),
    BRAND_IN_USE(2203, "该品牌下还有商品，不能删除"),

    /* ---------- 商品中心 · 属性 23xx ---------- */
    ATTRIBUTE_NOT_FOUND(2301, "属性不存在"),
    ATTRIBUTE_VALUE_REQUIRED(2302, "规格属性至少要有一个属性值，否则无法生成 SKU"),
    ATTRIBUTE_IN_USE(2303, "该属性已被商品使用，不能删除"),
    ATTRIBUTE_VALUE_IN_USE(2304, "该属性值已被 SKU 使用，不能删除，请改为禁用"),
    ATTRIBUTE_CATEGORY_MISMATCH(2305, "属性不属于所选类目"),

    /* ---------- 交易与订单 3xxx ---------- */
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_STATUS_CHANGED(3002, "订单状态已变更，请刷新后重试"),
    ORDER_STATUS_ILLEGAL(3003, "当前订单状态不支持该操作"),
    CART_EMPTY(3004, "请先选择要结算的商品"),
    ADDRESS_NOT_FOUND(3005, "收货地址不存在"),
    ADDRESS_LIMIT_EXCEED(3006, "收货地址数量已达上限"),

    /* ---------- 售后 32xx ---------- */
    AFTERSALE_NOT_FOUND(3201, "售后单不存在"),
    AFTERSALE_STATUS_ILLEGAL(3202, "当前售后状态不支持该操作"),
    AFTERSALE_NOT_ALLOWED(3203, "当前订单状态不支持申请售后"),
    AFTERSALE_ALREADY_EXISTS(3204, "该商品已存在售后申请，请勿重复提交"),
    AFTERSALE_TYPE_UNSUPPORTED(3205, "暂不支持该售后类型"),
    AFTERSALE_AMOUNT_INVALID(3206, "退款金额不合法"),
    AFTERSALE_NO_PAYMENT(3207, "该订单没有成功的支付记录，无法退款"),

    /* ---------- 支付 4xxx ---------- */
    PAYMENT_NOT_FOUND(4001, "支付单不存在"),
    PAY_SIGN_INVALID(4002, "支付回调验签失败"),
    PAY_AMOUNT_MISMATCH(4003, "支付金额与订单金额不一致"),
    REFUND_AMOUNT_EXCEED(4004, "退款金额超过可退金额"),
    PAY_CHANNEL_UNSUPPORTED(4005, "不支持的支付渠道"),
    PAY_ORDER_NOT_PAYABLE(4006, "当前订单状态不可支付"),

    /* ---------- 营销与秒杀 5xxx ---------- */
    COUPON_NOT_FOUND(5001, "优惠券不存在"),
    COUPON_USED_UP(5002, "优惠券已被领完"),
    COUPON_ALREADY_CLAIMED(5003, "您已领取过该优惠券"),
    COUPON_NOT_AVAILABLE(5004, "优惠券不可用"),
    SECKILL_NOT_STARTED(5101, "秒杀活动尚未开始"),
    SECKILL_ENDED(5102, "秒杀活动已结束"),
    SECKILL_SOLD_OUT(5103, "已售罄"),
    SECKILL_REPEAT(5104, "您已参与过本场秒杀"),
    SECKILL_QUEUEING(5105, "排队中，请稍后查看结果"),
    SECKILL_CHANNEL_UNAVAILABLE(5106, "秒杀通道未就绪，请稍后重试"),
    SECKILL_NOT_WARMED(5107, "活动尚未开始或库存未预热"),

    /* ---------- 幂等 6xxx ---------- */
    IDEMPOTENT_REPEAT(6001, "请求正在处理中，请勿重复提交");

    private final int code;
    private final String message;
}
