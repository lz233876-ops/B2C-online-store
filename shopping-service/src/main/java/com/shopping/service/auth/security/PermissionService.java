package com.shopping.service.auth.security;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * 权限判断服务，注册为名为 {@code ps} 的 Bean。
 * <p>
 * 用法（在 Controller 方法上）：
 * <pre>
 * &#64;PreAuthorize("&#64;ps.hasPerm('system:user:add')")
 * public Result&lt;Void&gt; add(&#64;RequestBody SysUserDTO dto) { ... }
 * </pre>
 * <p>
 * <b>为什么不直接用 {@code @PreAuthorize("hasAuthority('system:user:add')")}：</b>
 * 那样也能用，但把权限字符串散落在注解里，一旦要改命名规则（比如加模块前缀）
 * 就得全局搜索替换。抽一个 Bean 出来，判断逻辑集中在一处，
 * 而且可以方便地加「超级管理员无条件放行」这类全局规则。
 */
@Component("ps")
public class PermissionService {

    /** 通配符权限：拥有它等于拥有所有权限 */
    public static final String ALL_PERMISSION = "*:*:*";

    /** 超级管理员角色码 */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * 是否拥有指定权限。
     * <p>
     * 规则：超级管理员直接放行；否则看权限集合里有没有这个字符串。
     */
    public boolean hasPerm(String perm) {
        if (perm == null || perm.isBlank()) {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser == null) {
            return false;
        }
        if (isSuperAdmin(loginUser) || loginUser.getPerms().contains(ALL_PERMISSION)) {
            return true;
        }
        return loginUser.getPerms().contains(perm);
    }

    /** 拥有其中任意一个权限即可 */
    public boolean hasAnyPerm(String... perms) {
        if (perms == null || perms.length == 0) {
            return false;
        }
        Set<String> owned = ownedPerms();
        return Arrays.stream(perms).anyMatch(owned::contains);
    }

    /** 是否拥有指定角色（传角色码，不需要自己加 ROLE_ 前缀） */
    public boolean hasRole(String roleCode) {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser == null) {
            return false;
        }
        return loginUser.getRoles().contains(roleCode);
    }

    private boolean isSuperAdmin(LoginUser loginUser) {
        return loginUser.getRoles().contains(SUPER_ADMIN);
    }

    private Set<String> ownedPerms() {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser == null) {
            return Set.of();
        }
        if (isSuperAdmin(loginUser)) {
            return Set.of(ALL_PERMISSION);
        }
        return loginUser.getPerms();
    }
}
