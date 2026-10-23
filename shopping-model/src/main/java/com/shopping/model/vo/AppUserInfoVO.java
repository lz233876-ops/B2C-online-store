package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "会员信息")
public class AppUserInfoVO {

    @Schema(description = "会员ID")
    private Long userId;

    @Schema(description = "手机号（已脱敏）")
    private String phone;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "会员名（可空）")
    private String username;

    @Schema(description = "性别：0未知 1男 2女")
    private Integer gender;

    @Schema(description = "会员等级ID")
    private Long memberLevelId;

    @Schema(description = "成长值")
    private Integer growth;

    @Schema(description = "可用积分")
    private Integer points;
}
