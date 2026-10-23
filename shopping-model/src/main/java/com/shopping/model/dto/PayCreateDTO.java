package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "发起支付入参")
public class PayCreateDTO {

    @Schema(description = "订单号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @Schema(description = "支付渠道：1 模拟支付宝，2 模拟微信", defaultValue = "1")
    @NotNull(message = "请选择支付渠道")
    private Integer channel;
}
