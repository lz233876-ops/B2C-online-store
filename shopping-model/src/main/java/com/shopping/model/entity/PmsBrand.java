package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品牌（{@code pms_brand}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_brand")
public class PmsBrand extends BaseEntity {

    /** 品牌英文名，唯一 */
    private String name;

    /** 品牌中文名 */
    private String nameCn;

    /** 品牌 logo URL */
    private String logo;

    /** 首字母，用于按字母索引（A~Z） */
    private String firstLetter;

    /** 品牌介绍 */
    private String description;

    /** 排序，值越小越靠前 */
    private Integer sort;

    /** 状态：1 启用，0 禁用 */
    private Integer status;
}
