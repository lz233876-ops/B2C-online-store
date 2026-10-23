package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 购物车视图（列表 + 汇总）。
 * <p>
 * 汇总数字<b>只统计「已勾选且有效」的项</b>——因为用户结算的就是这些。
 * 如果把失效项也算进金额，用户会看到「结算页金额和购物车不一致」。
 */
@Data
@Schema(description = "购物车")
public class CartVO {

    @Schema(description = "购物车项列表")
    private List<CartItemVO> items = new ArrayList<>();

    @Schema(description = "购物车项总数（含失效项）")
    private Integer totalCount;

    @Schema(description = "商品总件数（含失效项的数量之和）")
    private Integer totalQuantity;

    @Schema(description = "已勾选且有效的项数")
    private Integer selectedCount;

    @Schema(description = "已勾选且有效的总件数")
    private Integer selectedQuantity;

    @Schema(description = "已勾选且有效的合计金额（分）")
    private Long selectedAmount;

    @Schema(description = "失效项数量（前端用于提示「有 N 件商品已失效」）")
    private Integer invalidCount;
}
