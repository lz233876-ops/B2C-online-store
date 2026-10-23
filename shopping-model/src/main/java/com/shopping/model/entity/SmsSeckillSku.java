package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 秒杀商品（{@code sms_seckill_sku}）—— 活动与 SKU 的关联表，<b>同时承载秒杀库存</b>。
 * <p>
 * <b>{@code seckill_stock} 与 {@code pms_sku.stock} 是两个独立概念：</b>
 * 前者是「本次活动投放的量」，后者是「仓库里真实的量」。
 * 一次秒杀应当<b>从真实库存里预留</b>一部分（{@code seckill_stock <= 可售库存}），
 * 这是运营配置的责任；下单时两边都要动（秒杀的 {@code seckill_sold} 与真实库存的锁定）。
 * <p>
 * <b>{@code seckill_sold} 的并发扣减有三道防线：</b>
 * <ol>
 *   <li><b>Redis 预扣</b>（Lua 原子脚本）—— 承担 99% 的并发，是主闸门；</li>
 *   <li><b>数据库条件更新</b> {@code WHERE seckill_sold + n <= seckill_stock} —— 兜底，防 Redis 数据漂移；</li>
 *   <li>唯一索引 {@code uk_activity_id_user_id}（在 record 表上）—— 防"一人多单"。</li>
 * </ol>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sms_seckill_sku")
public class SmsSeckillSku extends BaseEntity {

    private Long merchantId;
    private Long activityId;
    private Long spuId;
    private Long skuId;
    /** 秒杀价（分），必须低于原价 */
    private Long seckillPrice;
    /** 原价（分），用于展示划线价 */
    private Long originalPrice;
    /** 秒杀总库存（投放量） */
    private Integer seckillStock;
    /** 已售数量（并发扣减字段） */
    private Integer seckillSold;
    /** 每人限购数量（本项目实现为限购 1 件） */
    private Integer limitPerUser;
    private Integer sort;
    /** 1 启用，0 禁用 */
    private Integer status;
    /** 乐观锁版本号（备用方案，本项目用条件更新而非版本号） */
    private Integer version;
}
