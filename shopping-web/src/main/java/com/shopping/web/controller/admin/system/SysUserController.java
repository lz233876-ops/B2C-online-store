package com.shopping.web.controller.admin.system;

import com.shopping.common.result.PageResult;
import com.shopping.common.result.Result;
import com.shopping.model.query.SysUserQuery;
import com.shopping.model.vo.SysUserVO;
import com.shopping.service.system.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理接口。
 * <p>
 * <b>这是本项目第一个受 RBAC 保护的接口</b>，用来验证整条权限链路：
 * <pre>
 * 请求 → JwtAuthenticationFilter 装载 LoginUser（含 perms）
 *      → @PreAuthorize("@ps.hasPerm('system:user:list')")
 *      → PermissionService.hasPerm()：超管直通，否则查 perms 集合
 *      → 通过则执行，不通过则由 RestAccessDeniedHandler 返回 403
 * </pre>
 * <p>
 * ⚠️ 注意 {@code @PreAuthorize} 需要类或方法上开启：
 * 由 {@code SecurityConfig} 上的 {@code @EnableMethodSecurity} 统一开启。
 * 少了那个注解，这里会<b>完全不生效且没有任何报错</b>——
 * 是「明明配了权限却谁都能访问」的最常见原因。
 */
@Tag(name = "02-系统管理 · 后台用户", description = "后台用户的查询与维护")
@RestController
@RequestMapping("/api/admin/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @Operation(summary = "分页查询后台用户",
            description = "需要权限 system:user:list。返回的手机号与邮箱已脱敏，且不含密码字段")
    @PreAuthorize("@ps.hasPerm('system:user:list')")
    @GetMapping("/page")
    public Result<PageResult<SysUserVO>> page(SysUserQuery query) {
        return Result.success(sysUserService.page(query));
    }
}
