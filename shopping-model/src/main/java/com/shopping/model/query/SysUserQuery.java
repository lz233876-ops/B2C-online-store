package com.shopping.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台用户分页查询条件。
 * <p>
 * 所有条件都是可选的——传 null 表示不限制。判断「是否拼接该条件」的逻辑
 * 交给 MyBatis-Plus 的条件构造器（{@code like(condition, ...)} 的第一个参数），
 * 而不是在 Service 里写一堆 if。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "后台用户查询条件")
public class SysUserQuery extends PageQuery {

    @Schema(description = "登录账号，模糊匹配")
    private String username;

    @Schema(description = "昵称，模糊匹配")
    private String nickname;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;
}
