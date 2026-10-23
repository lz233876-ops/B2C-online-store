package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存流水（{@code wms_stock_log}）—— <b>幂等核心</b>。
 * <p>
 * <b>{@code (biz_type, biz_no)} 是幂等的钥匙。</b>{@code biz_no} 的取值必须
 * <b>确定性</b>：同一个业务动作无论重试多少次，算出来的 {@code biz_no} 都相同。
 * <table border="1">
 *   <tr><th>biz_type</th><th>场景</th><th>biz_no 取值</th></tr>
 *   <tr><td>1</td><td>下单锁定</td><td>{@code order_no}</td></tr>
 *   <tr><td>2</td><td>支付扣减</td><td>{@code payment_no}（一个订单可能多次支付尝试，所以不能用订单号）</td></tr>
 *   <tr><td>3</td><td>取消释放</td><td>{@code order_no + "_CANCEL"}</td></tr>
 *   <tr><td>4</td><td>退款回补</td><td>{@code refund_no}</td></tr>
 *   <tr><td>5</td><td>后台调整</td><td>{@code "ADJ_" + 雪花ID}（人工操作本身不幂等，用唯一 ID 保证每次是独立流水）</td></tr>
 *   <tr><td>6</td><td>秒杀扣减</td><td>{@code seckill_record_no}</td></tr>
 *   <tr><td>7</td><td>入库</td><td>{@code "IN_" + 雪花ID}</td></tr>
 * </table>
 * <b>规律：{@code biz_no} 永远是「业务单据号」，绝不包含时间戳或随机数</b>——
 * 否则重试时算出来的值不同，幂等就失效了。
 * <p>
 * <b>本表是 append-only</b>：只追加，永不修改、永不删除。{@code deleted} 恒为 0，
 * 代码里禁止任何针对本表的删除操作。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_stock_log")
public class WmsStockLog extends BaseEntity {

    /** 商家ID */
    private Long merchantId;

    /** SKU ID */
    private Long skuId;

    /** 仓库ID */
    private Long warehouseId;

    /** 业务类型：1下单锁定 2支付扣减 3取消释放 4退款回补 5后台调整 6秒杀扣减 7入库 */
    private Integer bizType;

    /** 业务幂等号，(biz_type, biz_no) 全局唯一 */
    private String bizNo;

    /** 关联订单号，便于按订单排查 */
    private String orderNo;

    /**
     * 变动数量（带符号）：锁定/扣减为负，释放/回补为正。
     * <p>用带符号单列而不是拆成 lock_num/deduct_num：{@code SUM(change_num)} 就是净变动，
     * 对账时一条 SQL 就能验证「流水汇总 == 库存现状」。
     */
    private Integer changeNum;

    /** 变动前可售库存 */
    private Integer beforeStock;

    /** 变动后可售库存 */
    private Integer afterStock;

    /** 操作人类型：1用户 2管理员 3系统 */
    private Integer operatorType;

    /** 操作人ID */
    private Long operatorId;

    /** 备注，如「超时关单释放」 */
    private String remark;

    /* ---------------- 业务类型常量 ---------------- */

    public static final int BIZ_LOCK = 1;
    public static final int BIZ_DEDUCT = 2;
    public static final int BIZ_RELEASE = 3;
    public static final int BIZ_REFUND = 4;
    public static final int BIZ_ADJUST = 5;
    public static final int BIZ_SECKILL = 6;
    public static final int BIZ_STOCK_IN = 7;

    /* ---------------- 操作人类型常量 ---------------- */

    public static final int OPERATOR_USER = 1;
    public static final int OPERATOR_ADMIN = 2;
    public static final int OPERATOR_SYSTEM = 3;
}
