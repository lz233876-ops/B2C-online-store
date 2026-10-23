package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "售后审核入参")
public class AftersaleAuditDTO {

    @Schema(description = "是否同意", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择审核结果")
    private Boolean approve;

    @Schema(description = "审核意见")
    @Size(max = 500, message = "审核意见不能超过 500 字")
    private String remark;

    /**
     * 实际退款金额（分）。审核时商家可以改价（比如只退部分运费）。
     * <p>不传则沿用用户申请的金额；<b>传了也必须小于等于可退金额</b>。
     */
    @Schema(description = "实际退款金额（分）；不传则按申请金额")
    private Long actualRefundAmount;
}
