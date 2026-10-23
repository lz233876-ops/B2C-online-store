package com.shopping.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台用户列表项。
 * <p>
 * <b>刻意不包含 {@code password} 字段</b>：这是最外层的一道防线。
 * 即使哪天有人在 Service 里不小心直接返回了实体，VO 里没有这个字段，
 * 密码也不会被序列化出去。把「不返回什么」用类型系统表达出来，
 * 比靠 code review 记住「不要返回密码」可靠得多。
 * <p>
 * 手机号在这里已经是<b>脱敏后</b>的值（由 Service 组装时调用
 * {@code MaskUtils.phone}）。脱敏放在组装 VO 时做，而不是在实体上做，
 * 因为业务逻辑（发短信、比对）需要真实值。
 */
@Data
@Schema(description = "后台用户列表项")
public class SysUserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "手机号（已脱敏）")
    private String phone;

    @Schema(description = "邮箱（已脱敏）")
    private String email;

    @Schema(description = "性别：0未知 1男 2女")
    private Integer gender;

    @Schema(description = "所属部门")
    private String deptName;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;

    @Schema(description = "最后登录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    @Schema(description = "最后登录IP")
    private String lastLoginIp;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
