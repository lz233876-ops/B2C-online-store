package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 前端动态路由树节点。
 * <p>
 * 与实体 {@code SysMenu} 的区别：这里是<b>为前端渲染准备好的形状</b>——
 * 字段名对齐前端路由约定（{@code name}/{@code path}/{@code component}），
 * 并且已经组装成树。后端不把数据库实体直接吐给前端，避免表结构一变前端就崩。
 * <p>
 * 只有 {@code menuType} 为 1（目录）和 2（菜单）的节点会出现在这棵树里；
 * 类型 3（按钮）只是权限点，通过 {@code AdminInfoVO.perms} 单独下发。
 */
@Data
@Schema(description = "前端动态路由节点")
public class MenuTreeVO {

    @Schema(description = "菜单ID")
    private Long id;

    @Schema(description = "父菜单ID")
    private Long parentId;

    @Schema(description = "路由名称（前端 keep-alive 与跳转用）")
    private String name;

    @Schema(description = "路由路径")
    private String path;

    @Schema(description = "组件路径")
    private String component;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "类型：1目录 2菜单")
    private Integer menuType;

    @Schema(description = "是否显示：1显示 0隐藏")
    private Integer visible;

    @Schema(description = "是否缓存：1是 0否")
    private Integer keepAlive;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "子节点")
    private List<MenuTreeVO> children = new ArrayList<>();
}
