package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "收货地址")
public class AddressVO {

    @Schema(description = "地址ID")
    private Long id;

    @Schema(description = "收货人姓名")
    private String receiverName;

    @Schema(description = "收货人手机号（已脱敏）")
    private String receiverPhone;

    @Schema(description = "省")
    private String province;

    @Schema(description = "市")
    private String city;

    @Schema(description = "区/县")
    private String district;

    @Schema(description = "详细地址")
    private String detailAddress;

    @Schema(description = "完整地址（省市区+详细，前端直接展示）")
    private String fullAddress;

    @Schema(description = "邮政编码")
    private String postalCode;

    @Schema(description = "是否默认：1是 0否")
    private Integer defaultStatus;

    @Schema(description = "标签")
    private String tag;
}
