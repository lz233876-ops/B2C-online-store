package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户-角色关联（{@code sys_user_role}）。
 * <p>
 * 为什么不把 {@code role_id} 直接放 {@code sys_user} 表？
 * 因为一个用户可能有多个角色（如「运营」+「财务」），一对多必须拆关联表。
 * 反过来，如果业务确定一个用户只能一个角色，那就该直接在 user 表放外键——
 * <b>不要为了「以后可能要多个」而预留关联表</b>，那是过度设计。
 * 本项目确实需要多角色，所以拆表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_role")
public class SysUserRole extends BaseEntity {

    /** 用户 ID，逻辑关联 {@code sys_user.id} */
    private Long userId;

    /** 角色 ID，逻辑关联 {@code sys_role.id} */
    private Long roleId;
}
