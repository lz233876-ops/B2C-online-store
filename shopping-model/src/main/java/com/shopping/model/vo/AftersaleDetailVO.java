package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "售后单详情")
public class AftersaleDetailVO extends AftersaleVO {

    @Schema(description = "问题描述")
    private String description;

    @Schema(description = "凭证图片URL列表")
    private List<String> images;

    @Schema(description = "审核意见")
    private String auditRemark;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "退货物流公司")
    private String returnCompany;

    @Schema(description = "退货物流单号")
    private String returnNo;

    @Schema(description = "买家寄回时间")
    private LocalDateTime returnTime;

    @Schema(description = "商家确认收货时间")
    private LocalDateTime receiveTime;

    @Schema(description = "超时自动关闭时间")
    private LocalDateTime autoCloseTime;

    @Schema(description = "退款单号（退款完成后才有）")
    private String refundNo;
}
