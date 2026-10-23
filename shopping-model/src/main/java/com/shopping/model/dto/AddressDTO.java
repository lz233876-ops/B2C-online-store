package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "收货地址新增/修改入参")
public class AddressDTO {

    @Schema(description = "主键；新增不传，修改必传")
    private Long id;

    @Schema(description = "收货人姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 64, message = "收货人姓名不能超过 64 字")
    private String receiverName;

    @Schema(description = "收货人手机号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收货人手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "收货人手机号格式不正确")
    private String receiverPhone;

    @Schema(description = "省", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "省份不能为空")
    @Size(max = 64, message = "省份不能超过 64 字")
    private String province;

    @Schema(description = "市", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "城市不能为空")
    @Size(max = 64, message = "城市不能超过 64 字")
    private String city;

    @Schema(description = "区/县", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "区县不能为空")
    @Size(max = 64, message = "区县不能超过 64 字")
    private String district;

    @Schema(description = "详细地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址不能超过 255 字")
    private String detailAddress;

    @Schema(description = "邮政编码")
    @Size(max = 16, message = "邮政编码不能超过 16 字")
    private String postalCode;

    @Schema(description = "标签：家/公司/学校")
    @Size(max = 32, message = "标签不能超过 32 字")
    private String tag;

    /**
     * 是否设为默认地址。
     * <p>注意：{@code userId} <b>不在入参里</b>，由服务端从登录态取。
     * 若让前端传 userId，攻击者改一个数字就能操作别人的地址（水平越权）。
     */
    @Schema(description = "是否设为默认：1是 0否", defaultValue = "0")
    private Integer defaultStatus;
}
