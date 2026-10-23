package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * SPU 详情（后台管理端）。
 * <p>继承 {@link SpuVO} 复用列表字段，再补上详情独有的部分——
 * 这样「列表字段变更」只需要改一个地方。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "SPU 详情")
public class SpuDetailVO extends SpuVO {

    @Schema(description = "搜索关键词")
    private String keywords;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "重量（克）")
    private Integer weight;

    @Schema(description = "详情 HTML")
    private String detailHtml;

    @Schema(description = "轮播图 URL 数组（冗余自图片表）")
    private List<String> albumImages = new ArrayList<>();

    @Schema(description = "SPU 级图片（可管理的数据源）")
    private List<ImageVO> images = new ArrayList<>();

    @Schema(description = "规格属性（type=1），前端据此渲染规格选择器")
    private List<AttrVO> specAttributes = new ArrayList<>();

    @Schema(description = "参数属性（type=2），前端据此渲染参数表")
    private List<AttrVO> paramAttributes = new ArrayList<>();

    @Schema(description = "SKU 列表（含成本价，仅后台）")
    private List<SkuVO> skus = new ArrayList<>();

    @Data
    @Schema(description = "商品图片")
    public static class ImageVO {
        @Schema(description = "图片URL")
        private String url;

        @Schema(description = "类型：1主图 2轮播图 3详情图")
        private Integer type;

        @Schema(description = "排序")
        private Integer sort;
    }

    @Data
    @Schema(description = "商品属性（含取值）")
    public static class AttrVO {
        @Schema(description = "属性ID")
        private Long attributeId;

        @Schema(description = "属性名")
        private String attributeName;

        @Schema(description = "类型：1规格属性 2参数属性")
        private Integer type;

        @Schema(description = "排序")
        private Integer sort;

        /**
         * 属性取值。
         * <p>规格属性：{@code values} 与 {@code valueIds} 一一对应（编辑表单需要 id 来回填选中态）；
         * 参数属性：只有 {@code values}（自由文本，没有 id）。
         */
        @Schema(description = "取值列表")
        private List<String> values = new ArrayList<>();

        @Schema(description = "取值ID列表，与 values 一一对应（仅规格属性有）")
        private List<Long> valueIds = new ArrayList<>();
    }
}
