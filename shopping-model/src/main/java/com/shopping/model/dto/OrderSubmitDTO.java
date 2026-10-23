package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "提交订单入参")
public class OrderSubmitDTO {

    /**
     * 收货地址 ID。
     * <p>⚠️ 只传地址 ID，<b>不传收货人信息</b>：服务端按 ID 查出地址再落快照。
     * 如果让前端直接把「收货人姓名/电话/地址」传上来，用户就能下一单寄到任意地址、
     * 填任意手机号——既是数据污染，也绕过了地址簿的校验。
     */
    @Schema(description = "收货地址ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    @Schema(description = "订单备注")
    @Size(max = 500, message = "备注不能超过 500 字")
    private String note;

    @Schema(description = "来源：1PC 2H5 3小程序 4App", defaultValue = "2")
    private Integer sourceType;
}
