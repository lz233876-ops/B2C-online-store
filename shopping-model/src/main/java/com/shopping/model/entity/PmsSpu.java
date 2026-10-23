package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 标准商品单元 SPU（{@code pms_spu}）。
 * <p>
 * <b>关于三个「库存/价格」字段的职责（面试高频，必须能讲清）：</b>
 * <table border="1">
 *   <tr><th>字段</th><th>性质</th><th>用途</th><th>能否用于交易判断</th></tr>
 *   <tr><td>{@code wms_stock.stock / available_stock}</td><td><b>权威</b></td><td>库存中心，条件更新</td><td>✅ 只有它能决定能不能卖</td></tr>
 *   <tr><td>{@code pms_sku.stock}</td><td>展示用冗余</td><td>库存变动后异步同步</td><td>❌ 绝对不能</td></tr>
 *   <tr><td>{@code pms_spu.stock}</td><td>展示用汇总</td><td>列表页显示「有货/无货」</td><td>❌ 绝对不能</td></tr>
 * </table>
 * <b>如果代码里用 {@code pms_sku.stock >= 1} 判断能不能下单，就是超卖事故</b>——
 * 两个并发请求会同时读到 {@code stock = 1}，都认为能卖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_spu")
public class PmsSpu extends BaseEntity {

    /** 商家ID，多商户预留：1 = 自营 */
    private Long merchantId;

    /** 类目ID */
    private Long categoryId;

    /** 品牌ID */
    private Long brandId;

    /** 商品名称，如「Apple iPhone 15」 */
    private String spuName;

    /** 副标题/卖点 */
    private String subTitle;

    /** 关键词，用于搜索 */
    private String keywords;

    /** 单位，如「台」「件」 */
    private String unit;

    /** 主图 URL */
    private String mainImage;

    /** 相册图，JSON 数组 */
    private String albumImages;

    /** 详情 HTML */
    private String detailHtml;

    /** 展示价：<b>冗余字段</b>，取该 SPU 下最低的 SKU 价格，只用于列表页 */
    private Long price;

    /** 原价（划线价），同样取最低 SKU 的原价 */
    private Long originalPrice;

    /** 总库存：<b>冗余汇总</b>，各 SKU 的 {@code stock} 之和，只用于列表页展示 */
    private Integer stock;

    /** 总销量 */
    private Integer saleCount;

    /** 重量（克） */
    private Integer weight;

    /**
     * 发布/审核状态：0 草稿，1 已提交待审，2 审核通过，3 审核驳回。
     * <p>⚠️ <b>这不是上下架</b>——上下架是 {@link #status}。两者容易搞混：
     * 一个商品可以「审核通过但已下架」（临时停售），也可以「审核通过且上架」。
     */
    private Integer publishStatus;

    /**
     * 上下架状态：1 上架，0 下架。
     * <p><b>C 端只看这个字段</b>：`publish_status = 2 AND status = 1` 才可购买。
     */
    private Integer status;

    /** 是否新品：1 是，0 否 */
    private Integer isNew;

    /** 是否推荐：1 是，0 否 */
    private Integer isRecommend;

    /**
     * 排序权重。
     * <p>⚠️ 注意与其它表的 {@code sort} <b>语义相反</b>：这里是
     * <b>值越大越靠前</b>（运营手动置顶用），而类目/品牌的 sort 是值越小越靠前。
     * 这是历史设计差异，用的时候别看错方向。
     */
    private Integer sort;

    /** 创建人（后台用户 ID） */
    private Long createBy;
}
