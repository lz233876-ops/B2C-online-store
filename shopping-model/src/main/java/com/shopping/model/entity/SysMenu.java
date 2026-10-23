package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单 / 权限点（{@code sys_menu}）。
 * <p>
 * 一张表承担三种角色，靠 {@code menuType} 区分：
 * <ul>
 *   <li>{@code 1} 目录 —— 前端侧边栏的一级分组，本身不可点击；</li>
 *   <li>{@code 2} 菜单 —— 对应一个路由页面；</li>
 *   <li>{@code 3} 按钮 —— <b>不出现在菜单里</b>，只作为权限点用于后端接口鉴权。</li>
 * </ul>
 * 把「按钮」也放进菜单表是主流做法：权限的<b>展示</b>（前端渲染哪些按钮）与
 * <b>校验</b>（后端 {@code @PreAuthorize} 放不放行）用同一份数据，
 * 不会出现「前端显示了按钮但后端没权限」这种数据不一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 父菜单 ID，0 表示根节点 */
    private Long parentId;

    /** 菜单名称 */
    private String menuName;

    /** 类型：1 目录，2 菜单，3 按钮 */
    private Integer menuType;

    /** 前端路由路径，如 {@code /system/user}（仅目录/菜单有） */
    private String path;

    /** 前端组件路径，如 {@code system/user/index}（仅菜单有） */
    private String component;

    /**
     * 权限标识，如 {@code system:user:add}。
     * <p>
     * 命名规范：{@code 模块:资源:动作}。后端用
     * {@code @PreAuthorize("@ps.hasPerm('system:user:add')")} 校验；
     * 前端用 {@code v-hasPerm="'system:user:add'"} 控制按钮显隐。
     * <p>
     * 目录和部分菜单没有权限标识（{@code null}），它们只用于路由组织。
     */
    private String perms;

    /** 菜单图标 */
    private String icon;

    /** 同级排序，值越小越靠前 */
    private Integer sort;

    /** 是否在菜单中显示：1 显示，0 隐藏（隐藏但仍可路由，如详情页） */
    private Integer visible;

    /** 是否缓存页面：1 是，0 否（对应前端 keep-alive） */
    private Integer keepAlive;

    /** 状态：1 启用，0 禁用 */
    private Integer status;
}
