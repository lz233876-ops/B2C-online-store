package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存（{@code wms_stock}）—— <b>全项目唯一能决定「能不能卖」的表</b>。
 * <p>
 * <b>三个库存字段的职责：</b>
 * <ul>
 *   <li>{@code stock} —— 实际总库存（含已锁定部分）；</li>
 *   <li>{@code lockedStock} —— 下单后已锁定、尚未支付的库存；</li>
 *   <li>{@code availableStock} —— 可售库存 = {@code stock - lockedStock}，
 *       是<b>数据库生成列（STORED GENERATED）</b>，只读。</li>
 * </ul>
 * <b>⚠️ {@code availableStock} 绝不能出现在 INSERT/UPDATE 里</b>——生成列不允许赋值，
 * 写了直接报 <b>ERROR 3105</b>。所以这里用 {@code FieldStrategy.NEVER} 从类型上封死，
 * 而不是靠"记得别 set 它"。
 * <p>
 * <b>扣减一律走条件更新</b>：
 * {@code UPDATE wms_stock SET stock = stock - #{n} WHERE sku_id = ? AND stock >= #{n}}
 * ——受影响行数为 0 即库存不足。<b>绝不能先 SELECT 判断再 UPDATE</b>，
 * 那样两个并发请求会同时读到 {@code stock = 1} 都认为能卖（超卖）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_stock")
public class WmsStock extends BaseEntity {

    /** 商家ID，多商户预留：1 = 自营 */
    private Long merchantId;

    /** SKU ID */
    private Long skuId;

    /** 仓库ID，Phase 1-3 固定为 1（单仓） */
    private Long warehouseId;

    /** 实际总库存（含已锁定部分） */
    private Integer stock;

    /** 下单已锁定、未支付的库存 */
    private Integer lockedStock;

    /**
     * 可售库存 = stock - lockedStock。
     * <p>数据库生成列，<b>只读</b>。写入会报 ERROR 3105，因此用 NEVER 策略彻底禁止参与增改。
     */
    @TableField(value = "available_stock", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer availableStock;

    /** 库存预警阈值，低于此值告警 */
    private Integer warnStock;

    /** 累计入库数量（对账用） */
    private Integer totalIn;

    /** 累计出库数量（对账用） */
    private Integer totalOut;

    /**
     * 乐观锁版本号。
     * <p>本项目库存扣减用<b>条件更新</b>（{@code WHERE stock >= n}）而不是版本号，
     * 所以这里<b>刻意不加 {@code @Version} 注解</b>——加了会让 MP 的乐观锁拦截器
     * 自动参与所有更新，与条件更新的语义叠加，反而容易出意外。
     * 该字段作为未来切换方案的备用（见 {@code docs/04} §7.3 的方案对比）。
     */
    private Integer version;
}
