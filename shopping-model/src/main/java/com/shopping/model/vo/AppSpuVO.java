package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * C 端商品（列表项）。
 * <p>
 * <b>⚠️ 这个 VO 的存在本身就是一道安全边界。</b>后台的 {@code SpuVO}/{@code SkuVO}
 * 里有 {@code costPrice}（成本价）—— 那是绝对不能给消费者的数据：
 * 泄露成本价等于把定价策略和毛利直接交给对手，比泄露用户手机号更伤商业利益。
 * <p>
 * 所以 C 端<b>不复用后台 VO，也不在 Controller 里「删字段」</b>，而是定义一套独立的 VO。
 * 复用 VO 再靠「记得别返回某个字段」是守不住的 —— 前后端一改需求就会漏。
 * <b>类型隔离比纪律可靠。</b>
 */
@Data
@Schema(description = "C 端商品（列表项）")
public class AppSpuVO {

    @Schema(description = "SPU ID")
    private Long id;

    @Schema(description = "商品名称")
    private String spuName;

    @Schema(description = "副标题/卖点")
    private String subTitle;

    @Schema(description = "主图")
    private String mainImage;

    @Schema(description = "展示价（分）= 最低 SKU 价")
    private Long price;

    @Schema(description = "展示价（元）")
    private String priceYuan;

    @Schema(description = "原价（分）")
    private Long originalPrice;

    @Schema(description = "原价（元）")
    private String originalPriceYuan;

    @Schema(description = "已售数量")
    private Integer saleCount;

    @Schema(description = "是否新品")
    private Integer isNew;

    @Schema(description = "是否推荐")
    private Integer isRecommend;

    @Schema(description = "品牌名")
    private String brandName;

    @Schema(description = "类目名")
    private String categoryName;

    @Schema(description = "SKU 数量")
    private Integer skuCount;

    @Schema(description = "可售库存合计（来自 Redis/权威库存）")
    private Integer availableStock;
}
