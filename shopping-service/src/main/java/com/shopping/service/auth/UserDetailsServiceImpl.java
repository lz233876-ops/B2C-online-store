package com.shopping.service.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.SysUserMapper;
import com.shopping.model.entity.SysUser;
import com.shopping.service.auth.security.LoginUser;
import com.shopping.service.system.SysPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 后台用户的登录主体装载器。
 * <p>
 * 同时实现两个接口，对应两个不同时机：
 * <ul>
 *   <li>{@link UserDetailsService#loadUserByUsername} —— <b>登录时</b>由
 *       {@code DaoAuthenticationProvider} 调用，用来校验密码；</li>
 *   <li>{@link LoginUserService#loadByUserId} —— <b>每次请求</b>由 JWT 过滤器调用，
 *       用来把 userId 还原成带权限的主体。</li>
 * </ul>
 * <p>
 * <b>它被谁调用：</b>{@code DaoAuthenticationProvider}。调用链是
 * {@code AuthenticationManager.authenticate()} →
 * {@code DaoAuthenticationProvider} → 本类 {@code loadUserByUsername} →
 * 拿到 {@code UserDetails} → 用 {@code PasswordEncoder.matches()} 比对密码。
 * <p>
 * <b>关于「用户不存在」为什么不抛明确的错误：</b>
 * 这里抛的是 {@link UsernameNotFoundException}，但
 * {@code DaoAuthenticationProvider} 默认
 * {@code hideUserNotFoundExceptions = true}，会把它转成 {@code BadCredentialsException}。
 * 这样做的目的是<b>避免用户名枚举</b>——如果「用户不存在」和「密码错误」
 * 返回不同提示，攻击者就能批量试出哪些账号真实存在。
 * 所以我们对外统一回复「用户名或密码错误」。
 * <p>
 * ⚠️ 本项目还有 C 端会员（{@code ums_user}）体系，会另有一个
 * {@code UmsUserDetailsService}，用不同的 {@code DaoAuthenticationProvider}。
 * 两者绝不能共用一个，否则会员账号能登进后台。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService, LoginUserService {

    private final SysUserMapper sysUserMapper;
    private final SysPermissionService sysPermissionService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (user == null) {
            // 会被 DaoAuthenticationProvider 转成 BadCredentialsException，避免用户名枚举
            throw new UsernameNotFoundException("后台用户不存在：" + username);
        }
        return assemble(user);
    }

    @Override
    public LoginUser loadByUserId(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.TOKEN_INVALID, "用户不存在或已被删除");
        }
        return assemble(user);
    }

    /**
     * 角色与权限<b>每次请求都从数据库读</b>，而不是写死在 token 里。
     * <p>
     * 好处：管理员调整了某角色的权限，用户下一次请求即生效（不必等 token 过期）。
     * 代价：每次请求 3 条 SQL（用户 + 角色 + 权限）。
     * <p>
     * 这是 Phase 1 的取舍。优化方向是把 {@code LoginUser} 以用户为粒度缓存进 Redis
     * （TTL 5~10 分钟，并在角色/菜单变更时主动失效），把 3 条 SQL 降为 0~1 次 Redis 读。
     * 详见 {@code docs/03-系统架构设计.md} §5 缓存设计。
     */
    private LoginUser assemble(SysUser user) {
        return new LoginUser(user,
                sysPermissionService.getRoleCodes(user.getId()),
                sysPermissionService.getPerms(user.getId()));
    }
}
