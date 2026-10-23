package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SPU 聚合保存入参。
 * <p>
 * <b>为什么是「聚合保存」而不是「先存 SPU、再存 SKU」多个接口：</b>
 * SPU、规格属性、图片、SKU 是一个业务整体。分成多个接口会产生中间态
 * （SPU 建好了但 SKU 没建成功 → 出现一个没有 SKU 的商品，无法售卖）。
 * 一个接口 + 一个事务，要么全成功要么全回滚。
 * <p>
 * <b>SKU 的生成方式：由前端做笛卡尔积，服务端校验。</b>
 * 理由：真实业务里很多组合并不存在（例如「红色 + 512G」可能根本没生产），
 * 前端需要根据已有 SKU 动态置灰不可选项（经典电商交互）。所以服务端
 * <b>不强制要求提交的组合等于全笛卡尔积</b>，但会严格校验：
 * <ol>
 *   <li>每个 SKU 的规格值必须来自本 SPU 声明的规格属性；</li>
 *   <li>每个 SKU 必须对每个规格属性都有且只有一个值；</li>
 *   <li>不能有两个 SKU 的组合完全相同。</li>
 * </ol>
 */
@Data
@Schema(description = "SPU 聚合保存入参")
public class SpuSaveDTO {

    @Schema(description = "主键；新增不传，修改必传")
    private Long id;

    @Schema(description = "类目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "类目不能为空")
    private Long categoryId;

    @Schema(description = "品牌ID（可为空）")
    private Long brandId;

    @Schema(description = "商品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 128, message = "商品名称不能超过 128 字")
    private String spuName;

    @Schema(description = "副标题/卖点")
    @Size(max = 255, message = "副标题不能超过 255 字")
    private String subTitle;

    @Schema(description = "搜索关键词，逗号分隔")
    @Size(max = 255, message = "关键词不能超过 255 字")
    private String keywords;

    @Schema(description = "单位", defaultValue = "件")
    @Size(max = 16, message = "单位不能超过 16 字")
    private String unit;

    @Schema(description = "重量（克）")
    @Min(value = 0, message = "重量不能为负")
    private Integer weight;

    @Schema(description = "排序权重，值越大越靠前", defaultValue = "0")
    private Integer sort;

    @Schema(description = "是否新品：1是 0否", defaultValue = "0")
    private Integer isNew;

    @Schema(description = "是否推荐：1是 0否", defaultValue = "0")
    private Integer isRecommend;

    @Schema(description = "详情 HTML")
    private String detailHtml;

    @Schema(description = "发布状态：0草稿 1待审 2通过 3驳回（默认 2 直接通过）", defaultValue = "2")
    private Integer publishStatus;

    @Schema(description = "上下架：1上架 0下架（默认 0，先存草稿再上架）", defaultValue = "0")
    private Integer status;

    @Schema(description = "SPU 级图片列表")
    @Valid
    private List<ImageItem> images;

    @Schema(description = "规格属性（type=1），决定生成哪些 SKU", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    private List<SpecAttributeItem> specAttributes;

    @Schema(description = "参数属性（type=2），仅用于详情页展示")
    @Valid
    private List<ParamAttributeItem> paramAttributes;

    /**
     * SKU 列表。
     * <p><b>至少一个</b>：没有 SKU 的 SPU 无法售卖，等于建了个空壳。
     */
    @Schema(description = "SKU 列表，至少一个", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少要有一个 SKU")
    @Valid
    private List<SkuItem> skus;

    /* ==================== 嵌套结构 ==================== */

    @Data
    @Schema(description = "SPU 级图片")
    public static class ImageItem {
        @Schema(description = "图片URL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "图片URL不能为空")
        @Size(max = 500, message = "图片URL过长")
        private String url;

        @Schema(description = "类型：1主图 2轮播图 3详情图", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "图片类型不能为空")
        private Integer type;

        @Schema(description = "排序", defaultValue = "0")
        private Integer sort;
    }

    @Data
    @Schema(description = "规格属性选择")
    public static class SpecAttributeItem {
        @Schema(description = "属性ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "规格属性ID不能为空")
        private Long attributeId;

        @Schema(description = "选中的属性值ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "规格属性至少要选一个属性值")
        private List<Long> valueIds;

        @Schema(description = "排序", defaultValue = "0")
        private Integer sort;
    }

    @Data
    @Schema(description = "参数属性取值")
    public static class ParamAttributeItem {
        @Schema(description = "属性ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "参数属性ID不能为空")
        private Long attributeId;

        @Schema(description = "参数值列表，如 [\"2023年\",\"全国联保\"]")
        private List<String> values;

        @Schema(description = "排序", defaultValue = "0")
        private Integer sort;
    }

    @Data
    @Schema(description = "SKU")
    public static class SkuItem {
        @Schema(description = "主键；新增不传。不传时服务端会按规格组合去匹配已有 SKU")
        private Long id;

        @Schema(description = "SKU编码；不传则自动生成")
        @Size(max = 64, message = "SKU编码不能超过 64 字")
        private String skuCode;

        @Schema(description = "销售价（分）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "SKU 销售价不能为空")
        @Min(value = 0, message = "销售价不能为负")
        private Long price;

        @Schema(description = "划线价（分），0 表示不展示")
        private Long originalPrice;

        @Schema(description = "成本价（分）")
        private Long costPrice;

        @Schema(description = "库存。首次保存会初始化 wms_stock；后续调库存请走库存中心", defaultValue = "0")
        @Min(value = 0, message = "库存不能为负")
        private Integer stock;

        @Schema(description = "重量（克），不传则用 SPU 的重量")
        private Integer weight;

        @Schema(description = "SKU 图片URL")
        @Size(max = 500, message = "图片URL过长")
        private String image;

        @Schema(description = "状态：1启用 0禁用", defaultValue = "1")
        private Integer status;

        @Schema(description = "该 SKU 的规格组合：每个规格属性一个值", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "SKU 必须指定规格组合")
        @Valid
        private List<SpecValueRef> specValues;
    }

    @Data
    @Schema(description = "规格值引用")
    public static class SpecValueRef {
        @Schema(description = "属性ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "属性ID不能为空")
        private Long attributeId;

        @Schema(description = "属性值ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "属性值ID不能为空")
        private Long valueId;
    }
}
