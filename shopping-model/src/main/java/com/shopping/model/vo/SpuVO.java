package com.shopping.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SPU 列表项（后台管理端）。
 * <p>{@code categoryName} / {@code brandName} 是为了让列表页不必再发请求查名字；
 * 服务层会用「批量查一次类目、批量查一次品牌」的方式填充，避免 N+1。
 */
@Data
@Schema(description = "SPU 列表项")
public class SpuVO {

    @Schema(description = "SPU ID")
    private Long id;

    @Schema(description = "商品名称")
    private String spuName;

    @Schema(description = "副标题/卖点")
    private String subTitle;

    @Schema(description = "类目ID")
    private Long categoryId;

    @Schema(description = "类目名称")
    private String categoryName;

    @Schema(description = "品牌ID")
    private Long brandId;

    @Schema(description = "品牌名称")
    private String brandName;

    @Schema(description = "主图URL")
    private String mainImage;

    @Schema(description = "展示价（分）—— 最低 SKU 价，冗余字段")
    private Long price;

    @Schema(description = "划线价（分）")
    private Long originalPrice;

    @Schema(description = "总库存 —— Σ 各 SKU 的展示库存，冗余字段")
    private Integer stock;

    @Schema(description = "累计销量")
    private Integer saleCount;

    @Schema(description = "SKU 数量")
    private Integer skuCount;

    @Schema(description = "发布状态：0草稿 1待审 2通过 3驳回")
    private Integer publishStatus;

    @Schema(description = "上下架：1上架 0下架")
    private Integer status;

    @Schema(description = "是否新品：1是 0否")
    private Integer isNew;

    @Schema(description = "是否推荐：1是 0否")
    private Integer isRecommend;

    @Schema(description = "排序权重，值越大越靠前")
    private Integer sort;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
