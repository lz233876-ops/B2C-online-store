package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.SysMenu;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单 / 权限点 Mapper。
 * <p>
 * 两个查询都是四表关联（menu → role_menu → user_role → role），手写 XML。
 * ⚠️ <b>手写 SQL 不会自动带上 MyBatis-Plus 的逻辑删除条件</b>，
 * 必须自己写 {@code deleted = 0}——漏了就会把已删除的角色/菜单算进权限，
 * 这是很容易踩的坑。
 */
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查某个用户拥有的全部权限标识（按钮级）。
     * <p>
     * 只返回非空的 {@code perms}，因为目录和部分菜单没有权限标识。
     *
     * @param userId 用户 ID
     * @return 权限标识列表，如 {@code [system:user:list, system:user:add]}
     */
    List<String> selectPermsByUserId(@Param("userId") Long userId);

    /**
     * 查某个用户可见的菜单（仅目录与菜单，不含按钮），用于组装前端动态路由。
     *
     * @param userId 用户 ID
     * @return 已按 {@code parentId, sort, id} 排好序的菜单列表（扁平）
     */
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);
}
