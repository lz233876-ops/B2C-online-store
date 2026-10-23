package com.shopping.service.inventory;

/**
 * 库存服务 —— <b>库存的唯一入口</b>。
 * <p>
 * <b>为什么所有库存变动都必须走这里：</b>
 * 因为「改库存」永远要同时做两件事——① 更新 {@code wms_stock}（权威值）、
 * ② 写 {@code wms_stock_log}（流水）。任何绕过本服务直接 UPDATE 的代码，
 * 都会造成「流水与库存对不上」，而对不上账的库存等于没有库存。
 * <p>
 * Phase 1 只实现「初始化」与「后台调整」；Phase 2 补上
 * 下单锁定 / 支付扣减 / 取消释放 / 退款回补，以及 Redis 预扣挡流量。
 */
public interface StockService {

    /**
     * 为 SKU 初始化库存（SPU 首次保存时调用）。
     * <p>若记录已存在则退化为 {@link #adjustStock}。
     *
     * @param skuId  SKU ID
     * @param stock  初始库存
     * @param remark 备注，会写进流水
     */
    void initStock(Long skuId, int stock, String remark);

    /**
     * 后台调整库存（SPU 编辑时调用）。
     * <p>会写一条 {@code biz_type=5} 的流水，{@code biz_no = "ADJ_" + 雪花ID}
     * ——人工调整本身不幂等，用唯一 ID 保证每次是一条独立流水。
     *
     * @throws com.shopping.common.exception.BizException 新库存小于已锁定库存时
     */
    void adjustStock(Long skuId, int newStock, String remark);

    /** 查可售库存（{@code stock - locked_stock}）；无记录返回 0 */
    int getAvailableStock(Long skuId);

    /**
     * <b>批量</b>查可售库存，返回 {@code skuId -> 可售数量}。
     * <p>为什么要有批量版本：购物车列表、商品详情等场景要展示一批 SKU 的库存，
     * 逐个调用 {@link #getAvailableStock} 就是 N+1 查询。
     * 一次 {@code IN} 查完，10 个 SKU 从 10 次查询降到 1 次。
     * <p>查不到的 SKU 不会出现在返回的 Map 里（调用方按 0 处理）。
     */
    java.util.Map<Long, Integer> getAvailableStocks(java.util.Collection<Long> skuIds);

    /* ==================== Phase 2：交易的四个原子操作 ==================== */

    /**
     * ① <b>下单锁定</b>：把可售库存冻结起来（只动 {@code locked_stock}，货还在仓库）。
     * <p>流水：{@code biz_type=1}，{@code biz_no=orderNo}。
     *
     * @throws com.shopping.common.exception.BizException 可售库存不足时抛 {@code STOCK_NOT_ENOUGH}
     */
    void lockStock(Long skuId, int num, String orderNo);

    /**
     * ② <b>支付扣减</b>：冻结转实扣。
     * <p>⚠️ {@code stock} 与 {@code locked_stock} 同时减，所以<b>可售库存不变</b>——
     * 库存其实在锁定那一刻就已经让出去了。因此这一步的流水
     * {@code change_num = 0}（对可售库存的净影响为零）。
     * <p>流水：{@code biz_type=2}，{@code biz_no=paymentNo}（一个订单可能多次支付尝试，
     * 所以不能用订单号，否则第二次支付会被幂等索引挡掉）。
     *
     * @throws com.shopping.common.exception.BizException 冻结库存不足时抛 {@code STOCK_DEDUCT_FAILED}
     */
    void deductStock(Long skuId, int num, String orderNo, String paymentNo);

    /**
     * ③ <b>取消/超时释放</b>：冻结还回可售。
     * <p>流水：{@code biz_type=3}，{@code biz_no = orderNo + "_CANCEL"}。
     * <p><b>幂等由流水唯一索引 {@code (biz_type, biz_no)} 兜底</b>：
     * 重复调用时日志插入撞唯一键 → 整个事务回滚 → 库存不会被重复释放。
     */
    void releaseStock(Long skuId, int num, String orderNo);

    /**
     * ④ <b>退款回补</b>：货退回，可售库存增加并计入累计入库。
     * <p>流水：{@code biz_type=4}，{@code biz_no=refundNo}。
     */
    void refundStock(Long skuId, int num, String orderNo, String refundNo);
}
