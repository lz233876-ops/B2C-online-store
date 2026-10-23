package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "售后单（列表项）")
public class AftersaleVO {

    @Schema(description = "售后单ID")
    private Long id;

    @Schema(description = "售后单号")
    private String aftersaleNo;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "订单明细ID")
    private Long orderItemId;

    @Schema(description = "售后类型：1仅退款 2退货退款")
    private Integer type;

    @Schema(description = "售后类型描述")
    private String typeDesc;

    @Schema(description = "售后状态码")
    private Integer status;

    @Schema(description = "售后状态描述")
    private String statusDesc;

    @Schema(description = "申请原因")
    private String reason;

    @Schema(description = "申请退款金额（分）")
    private Long refundAmount;

    @Schema(description = "实际退款金额（分）")
    private Long actualRefundAmount;

    @Schema(description = "退货数量")
    private Integer quantity;

    @Schema(description = "商品名称（订单快照）")
    private String itemName;

    @Schema(description = "商品图片（订单快照）")
    private String itemImage;

    @Schema(description = "规格摘要（订单快照）")
    private String specDesc;

    @Schema(description = "该明细实付金额（分）")
    private Long itemPayAmount;

    @Schema(description = "申请时间")
    private LocalDateTime createTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;
}
