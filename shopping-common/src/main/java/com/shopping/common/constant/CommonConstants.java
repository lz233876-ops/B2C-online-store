package com.shopping.common.constant;

/**
 * 全局常量。
 * <p>
 * 约定：常量集中在此，禁止在业务代码里散落魔法值。
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /* ---------------- 通用 ---------------- */

    /** 链路追踪标识，放在 MDC 与响应头里 */
    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 默认商家 ID：Phase 1-3 为自营单商户，多商户维度先预留 */
    public static final Long DEFAULT_MERCHANT_ID = 1L;

    /* ---------------- 逻辑删除 ---------------- */

    public static final int NOT_DELETED = 0;
    public static final int DELETED = 1;

    /* ---------------- 启用状态 ---------------- */

    public static final int DISABLED = 0;
    public static final int ENABLED = 1;

    /* ---------------- 分页 ---------------- */

    public static final long DEFAULT_PAGE_CURRENT = 1L;
    public static final long DEFAULT_PAGE_SIZE = 10L;
    /** 单页最大条数，防止前端传 size=100000 拖垮数据库 */
    public static final long MAX_PAGE_SIZE = 100L;

    /* ---------------- 认证 ---------------- */

    public static final String AUTH_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    /* ---------------- Redis key 前缀 ---------------- */
    /** 统一规范：shopping:{模块}:{业务}:{标识} */
    public static final String REDIS_PREFIX = "shopping:";
}
