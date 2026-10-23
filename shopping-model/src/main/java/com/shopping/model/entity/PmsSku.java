package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 最小库存单元 SKU（{@code pms_sku}）。
 * <p>
 * <b>「一个 SKU = 一个可售卖组合」是理解整个商品模型的关键。</b>
 * SPU「iPhone 15」有 4 个 SKU：「黑色 128G」「黑色 256G」「白色 128G」「白色 256G」。
 * 每个 SKU 有<b>独立的</b>价格、库存、图片、条码——因为它们的成本和销量都不同。
 * <p>
 * <b>⚠️ 两个 stock 的关系（最容易出事故的地方）：</b>
 * <ul>
 *   <li>{@code pms_sku.stock} —— <b>展示用冗余</b>。库存变动后由 MQ 异步刷。</li>
 *   <li>{@code wms_stock.stock / available_stock} —— <b>权威库存</b>。只有它能决定能不能卖。</li>
 * </ul>
 * <b>用 {@code pms_sku.stock >= 1} 判断能否下单，就是超卖事故</b>——两个并发请求会同时
 * 读到 {@code stock = 1} 都认为能卖。详见 {@code docs/04} §4.1 坑 3。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_sku")
public class PmsSku extends BaseEntity {

    /** 商家ID，多商户预留 */
    private Long merchantId;

    /** SPU ID */
    private Long spuId;

    /** SKU 编码，如 {@code IP15-BLK-128}，全局唯一（与 deleted 联合） */
    private String skuCode;

    /** SKU 名称，如「Apple iPhone 15 128GB 黑色」 */
    private String skuName;

    /** 规格摘要，如「128GB / 黑色」。冗余用于列表/购物车直接展示，不必解析 JSON */
    private String specDesc;

    /**
     * 规格快照 JSON 数组。
     * <p>形如：{@code [{"attributeId":1,"attributeName":"颜色","valueId":3,"valueName":"黑色"}]}
     * <p>与规范化表 {@code pms_sku_attribute_value} 是「一份数据两种表示」：
     * 这里用于<b>渲染</b>（整块读出，不参与 WHERE）；那张表用于<b>筛选与统计</b>（走索引）。
     */
    private String specJson;

    /** SKU 图片 URL */
    private String image;

    /** 销售价（单位：分） */
    private Long price;

    /** 划线价（单位：分），0 表示不展示 */
    private Long originalPrice;

    /** 成本价（单位：分）。<b>仅商家后台可见，C 端接口必须排除</b> */
    private Long costPrice;

    /** 可售库存（冗余自 {@code wms_stock}，仅用于展示） */
    private Integer stock;

    /** 锁定库存（冗余展示用） */
    private Integer lockedStock;

    /** 累计销量 */
    private Integer saleCount;

    /** 重量（克），用于运费计算 */
    private Integer weight;

    /** 状态：1 启用，0 禁用 */
    private Integer status;
}
