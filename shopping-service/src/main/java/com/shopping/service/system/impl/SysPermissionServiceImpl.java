package com.shopping.service.system.impl;

import com.shopping.dao.mapper.SysMenuMapper;
import com.shopping.dao.mapper.SysRoleMapper;
import com.shopping.model.entity.SysMenu;
import com.shopping.model.vo.MenuTreeVO;
import com.shopping.service.system.SysPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限查询实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    /** 根节点的 parentId */
    private static final long ROOT_PARENT_ID = 0L;

    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;

    @Override
    public Set<String> getRoleCodes(Long userId) {
        List<String> codes = sysRoleMapper.selectRoleCodesByUserId(userId);
        return codes == null ? Set.of() : new HashSet<>(codes);
    }

    @Override
    public Set<String> getPerms(Long userId) {
        List<String> perms = sysMenuMapper.selectPermsByUserId(userId);
        return perms == null ? Set.of() : new HashSet<>(perms);
    }

    /**
     * 扁平列表 → 树。
     * <p>
     * 采用<b>两趟扫描</b>：第一趟把所有节点转成 VO 并按 id 建索引；
     * 第二趟把每个节点挂到父节点的 children 上。时间复杂度 O(n)，
     * 比「对每个节点递归查一次子节点」的 O(n²) 好得多，
     * 也避免了 N 次数据库查询（那是典型的 N+1 问题）。
     * <p>
     * <b>关于「父节点不在结果集里」的处理：</b>
     * 如果某个节点有权限、但它的父目录没被授权，那么挂不上去。
     * 这时把它<b>当作根节点</b>输出——否则用户会「看不到自己明明有权限的页面」。
     * 这种「授权数据不完整」在手工配权限时很常见，必须容错。
     */
    @Override
    public List<MenuTreeVO> getMenuTree(Long userId) {
        List<SysMenu> menus = sysMenuMapper.selectMenusByUserId(userId);
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }

        // 第一趟：转 VO 并建索引（LinkedHashMap 保持数据库返回的顺序，即已按 parentId,sort 排好）
        Map<Long, MenuTreeVO> idToNode = new LinkedHashMap<>(menus.size());
        for (SysMenu menu : menus) {
            idToNode.put(menu.getId(), toVO(menu));
        }

        // 第二趟：挂载
        List<MenuTreeVO> roots = new ArrayList<>();
        for (MenuTreeVO node : idToNode.values()) {
            Long parentId = node.getParentId();
            if (parentId == null || parentId == ROOT_PARENT_ID) {
                roots.add(node);
                continue;
            }
            MenuTreeVO parent = idToNode.get(parentId);
            if (parent == null) {
                // 父目录未被授权 —— 当作根节点输出，保证有权限的页面一定能看到
                log.debug("菜单 [{}] 的父节点 [{}] 不在授权范围内，按根节点处理", node.getId(), parentId);
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private MenuTreeVO toVO(SysMenu menu) {
        MenuTreeVO vo = new MenuTreeVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setName(menu.getMenuName());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setIcon(menu.getIcon());
        vo.setMenuType(menu.getMenuType());
        vo.setVisible(menu.getVisible());
        vo.setKeepAlive(menu.getKeepAlive());
        vo.setSort(menu.getSort());
        vo.setChildren(new ArrayList<>());
        return vo;
    }
}
