package com.shopping.service.auth.security;

import com.shopping.model.entity.SysUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录用户主体，Spring Security 认证通过后放进 {@code SecurityContextHolder} 的就是它。
 * <p>
 * <b>为什么实现 {@link UserDetails} 而不是到处传 {@code SysUser}：</b>
 * Spring Security 的认证、授权、表达式求值全部面向 {@code UserDetails} 与
 * {@code GrantedAuthority}。把实体直接塞进去，就得在每个用到的地方自己转换权限，
 * 逻辑会散落一地。
 * <p>
 * <b>权限装配的两个细节：</b>
 * <ol>
 *   <li>角色要加 {@code ROLE_} 前缀。Spring Security 的 {@code hasRole("X")}
 *       内部匹配的是字面量 {@code ROLE_X}，不加前缀永远不匹配——这是新手最常踩的坑；</li>
 *   <li>权限标识（{@code system:user:add}）<b>不加前缀</b>，原样作为 authority，
 *       这样 {@code hasAuthority("system:user:add")} 才能命中。</li>
 * </ol>
 */
@Getter
public class LoginUser implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String password;
    private final String nickname;
    private final String avatar;
    private final String phone;

    /** 是否启用：1 启用，0 禁用 */
    private final Integer status;

    /** 角色编码，如 {@code [SUPER_ADMIN]} */
    private final Set<String> roles;

    /** 权限标识，如 {@code [system:user:list, system:user:add]} */
    private final Set<String> perms;

    /** Spring Security 用的权限集合，由 roles + perms 装配而成 */
    private final Set<GrantedAuthority> authorities;

    public LoginUser(SysUser user, Set<String> roles, Set<String> perms) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.nickname = user.getNickname();
        this.avatar = user.getAvatar();
        this.phone = user.getPhone();
        this.status = user.getStatus();
        this.roles = roles == null ? Set.of() : roles;
        this.perms = perms == null ? Set.of() : perms;

        Set<GrantedAuthority> auths = new LinkedHashSet<>();
        // 角色加 ROLE_ 前缀，供 hasRole() 使用
        this.roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .forEach(auths::add);
        // 权限标识原样，供 hasAuthority() / 自定义 hasPerm() 使用
        this.perms.stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(auths::add);
        this.authorities = Set.copyOf(auths);
    }

    /**
     * 供 Redis 缓存反序列化使用（本项目暂未缓存 LoginUser，但先留好构造入口）。
     */
    public LoginUser(Long userId, String username, String password, String nickname,
                     String avatar, String phone, Integer status,
                     Set<String> roles, Set<String> perms) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.avatar = avatar;
        this.phone = phone;
        this.status = status;
        this.roles = roles == null ? Set.of() : roles;
        this.perms = perms == null ? Set.of() : perms;
        Set<GrantedAuthority> auths = new LinkedHashSet<>();
        this.roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).forEach(auths::add);
        this.perms.stream().map(SimpleGrantedAuthority::new).forEach(auths::add);
        this.authorities = Set.copyOf(auths);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账号是否启用。
     * <p>
     * 返回 false 时 Spring Security 会抛 {@code DisabledException}，
     * 认证阶段就被拦下——比自己写 if 判断可靠，因为任何走
     * {@code AuthenticationManager} 的路径都自动生效。
     */
    @Override
    public boolean isEnabled() {
        return status != null && status == 1;
    }

    /** 权限集合的可读形式，便于打日志排查 */
    public String authoritySummary() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
    }
}
