package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "申请售后入参")
public class AftersaleApplyDTO {

    /**
     * 订单明细 ID —— <b>售后针对的是「某个商品」，不是整笔订单</b>。
     * <p>一笔订单可能买 3 件商品，用户只想退其中 1 件。传订单 ID 就表达不了这个诉求。
     */
    @Schema(description = "订单明细ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择要退款的商品")
    private Long orderItemId;

    @Schema(description = "售后类型：1 仅退款，2 退货退款", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择售后类型")
    private Integer type;

    @Schema(description = "申请原因，如「商品破损」「七天无理由」", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写申请原因")
    @Size(max = 255, message = "申请原因不能超过 255 字")
    private String reason;

    @Schema(description = "问题描述")
    @Size(max = 1000, message = "问题描述不能超过 1000 字")
    private String description;

    /**
     * 申请退款金额（分）。可以不传，服务端按「实付单价 × 退货数量」兜底。
     * <p>⚠️ 但<b>无论传不传，服务端都必须校验上限</b>：
     * 绝不能允许退款金额超过该明细的实付金额（那是白送钱）。
     */
    @Schema(description = "申请退款金额（分）；不传则按实付单价×数量计算")
    @Min(value = 0, message = "退款金额不能为负")
    private Long refundAmount;

    @Schema(description = "退货数量；不传默认 1", defaultValue = "1")
    @Min(value = 1, message = "退货数量至少为 1")
    @Max(value = 999, message = "退货数量过大")
    private Integer quantity;

    @Schema(description = "凭证图片URL列表")
    private List<String> images;
}
