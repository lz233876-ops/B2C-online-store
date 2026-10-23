package com.shopping.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "会员注册入参")
public class AppRegisterDTO {

    /**
     * 手机号是 C 端登录主标识。
     * <p>用正则而不是 {@code @Size} 约束：格式不合法的手机号存进库，
     * 以后发短信、找回密码全都会失败，越早拦住越好。
     */
    @Schema(description = "手机号（登录主标识）", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800001111")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 之间")
    private String password;

    @Schema(description = "昵称，不传则用手机号脱敏值")
    @Size(max = 64, message = "昵称不能超过 64 字")
    private String nickname;

    @Schema(description = "注册来源：1PC 2H5 3小程序 4App", defaultValue = "2")
    private Integer registerSource;
}
