package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色-菜单关联（{@code sys_role_menu}）。
 * <p>
 * 注意它关联的是 {@code sys_menu.id}，而菜单表里既有「目录/菜单」也有「按钮」。
 * 所以这张表同时表达了<b>能看到哪些页面</b>和<b>能点哪些按钮</b>——
 * 这是本项目权限模型简洁的关键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_menu")
public class SysRoleMenu extends BaseEntity {

    /** 角色 ID，逻辑关联 {@code sys_role.id} */
    private Long roleId;

    /** 菜单/权限点 ID，逻辑关联 {@code sys_menu.id} */
    private Long menuId;
}
