package com.shopping.service.auth.security;

import com.shopping.model.entity.UmsUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * C 端会员的登录主体。
 * <p>
 * <b>为什么要和后台的 {@code LoginUser} 分成两个类：</b>
 * 两者是<b>两套权限模型</b>。后台是 RBAC（用户→角色→权限点），
 * 会员没有角色也没有权限点，只有「是谁」。如果共用一个主体类，
 * 就会出现「会员主体被当成后台主体」时权限集合为空的边界情况，
 * 而空权限在有 {@code hasPerm} 判断的地方是「全部拒绝」、在没判断的地方是「全部放行」——
 * <b>用两个类型把这条边界画出来，比靠代码审查记住「这里要判 userScope」可靠。</b>
 * <p>
 * 同理，两个 {@code AuthenticationManager}、两个 {@code UserDetailsService}、
 * 两个 JWT 过滤器，都是这条边界的延伸。
 */
@Getter
public class AppLoginUser implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会员唯一角色：用于未来扩展会员侧的功能开关（如 PLUS 会员） */
    public static final String ROLE_MEMBER = "ROLE_MEMBER";

    private final Long userId;
    private final String phone;
    private final String password;
    private final String nickname;
    private final String avatar;
    private final Integer status;

    public AppLoginUser(UmsUser user) {
        this.userId = user.getId();
        this.phone = user.getPhone();
        this.password = user.getPassword();
        this.nickname = user.getNickname();
        this.avatar = user.getAvatar();
        this.status = user.getStatus();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 会员没有 RBAC 权限点，给一个固定角色即可
        return List.of(new SimpleGrantedAuthority(ROLE_MEMBER));
    }

    /** 登录标识是手机号，不是 {@code username} */
    @Override
    public String getUsername() {
        return phone;
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

    @Override
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
