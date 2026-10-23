package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "买家填写退货物流入参")
public class AftersaleReturnDTO {

    @Schema(description = "退货物流公司", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写退货物流公司")
    @Size(max = 64, message = "物流公司名称过长")
    private String returnCompany;

    @Schema(description = "退货物流单号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写退货物流单号")
    @Size(max = 64, message = "物流单号过长")
    private String returnNo;
}
