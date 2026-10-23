package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色（{@code sys_role}）。
 * <p>
 * RBAC 的核心：用户 → 角色 → 菜单/权限点。
 * 权限判定一律基于<b>角色码</b>（{@code roleCode}）与<b>权限标识</b>（{@code sys_menu.perms}），
 * 不基于角色名——角色名会改，角色码不会。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色名称，如「超级管理员」 */
    private String roleName;

    /**
     * 角色编码，如 {@code SUPER_ADMIN} / {@code OPERATOR}。
     * <p>
     * Spring Security 里用 {@code ROLE_} 前缀的权限表达（{@code hasRole("SUPER_ADMIN")}
     * 实际匹配的是 {@code ROLE_SUPER_ADMIN}），所以装配权限时要注意加前缀。
     */
    private String roleCode;

    /**
     * 数据范围：1 全部，2 本商户，3 仅本人。
     * <p>
     * Phase 1-3 是自营单商户，该字段只读取不使用；
     * 等 Phase 4 做多商户时，它就是数据隔离的开关。
     */
    private Integer dataScope;

    /** 排序，值越小越靠前 */
    private Integer sort;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
