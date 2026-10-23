package com.shopping.service.system;

import com.shopping.model.vo.MenuTreeVO;

import java.util.List;
import java.util.Set;

/**
 * 权限查询服务：把「用户 → 角色 → 权限/菜单」这段链路封装起来。
 */
public interface SysPermissionService {

    /**
     * 查用户拥有的<b>启用状态</b>角色编码。
     *
     * @return 空集合而不是 null，调用方不必判空
     */
    Set<String> getRoleCodes(Long userId);

    /**
     * 查用户拥有的全部权限标识（按钮级）。
     *
     * @return 空集合而不是 null
     */
    Set<String> getPerms(Long userId);

    /**
     * 组装用户可见的前端动态路由树。
     *
     * @return 根节点列表；无权限时为空列表
     */
    List<MenuTreeVO> getMenuTree(Long userId);
}
