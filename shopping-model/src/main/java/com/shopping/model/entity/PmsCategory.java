package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品类目（{@code pms_category}），自关联树结构。
 * <p>
 * 本项目限制<b>最多三级</b>（{@code level} 1~3）。为什么要限制：
 * 超过三级的类目在 C 端导航里已经无法优雅展示，而且筛选条件的组合会迅速复杂化。
 * 把限制写进业务规则（而不是"理论上支持无限级"），是务实的做法。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_category")
public class PmsCategory extends BaseEntity {

    /** 父类目 ID，0 表示根节点 */
    private Long parentId;

    /** 类目名称 */
    private String name;

    /** 层级：1 一级，2 二级，3 三级。由 parent 派生，不由前端传 */
    private Integer level;

    /** 同级排序，值越小越靠前 */
    private Integer sort;

    /** 图标 URL */
    private String icon;

    /** 类目 banner 图 URL */
    private String banner;

    /** 前台是否展示：1 展示，0 隐藏 */
    private Integer isShow;

    /** 状态：1 启用，0 禁用 */
    private Integer status;
}
