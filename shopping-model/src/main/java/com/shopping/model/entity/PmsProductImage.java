package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品图片（{@code pms_product_image}）。
 * <p>
 * <b>为什么已有 {@code pms_spu.main_image} / {@code pms_spu.album_images} 还要这张表：</b>
 * 那两列是<b>为列表页准备的反规范化缓存</b>（一次查询就能拿到主图和轮播图，
 * 不用 JOIN 图片表）；本表是<b>可管理的规范化数据</b>——运营要增删图、调顺序、给 SKU 绑定独立图片。
 * 保存商品时<b>双写</b>：本表存全量，同时把主图/轮播图汇总写回 {@code pms_spu} 的两个列。
 * <p>
 * <b>{@code skuId = 0} 表示 SPU 级图片</b>：用哨兵值而不是 NULL，因为这个字段参与
 * 联合索引，NULL 在索引里不参与唯一性比较，用 0 更直观也更安全。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_product_image")
public class PmsProductImage extends BaseEntity {

    /** SPU ID */
    private Long spuId;

    /** SKU ID；{@code 0} 表示 SPU 级图片（哨兵值，不用 NULL） */
    private Long skuId;

    /** 图片 URL */
    private String url;

    /** 图片类型：1 主图，2 轮播图，3 详情图 */
    private Integer type;

    /** 排序，值越小越靠前 */
    private Integer sort;
}
