package com.shopping.service.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.UmsUserMapper;
import com.shopping.model.entity.UmsUser;
import com.shopping.service.auth.security.AppLoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * C 端会员的登录主体装载器。
 * <p>
 * <b>本类的存在本身就是一道安全边界。</b>后台的 {@code UserDetailsServiceImpl}
 * 查的是 {@code sys_user}，本类查的是 {@code ums_user}。
 * 两者分别挂在不同的 {@code DaoAuthenticationProvider} 上，
 * 于是「拿会员手机号去登后台」在认证阶段就会失败——
 * 而不是等到鉴权阶段才发现「这个主体没有权限」。<b>失败得更早、更干净。</b>
 */
@Service
@RequiredArgsConstructor
public class UmsUserDetailsService implements UserDetailsService {

    private final UmsUserMapper umsUserMapper;

    /** 登录时按<b>手机号</b>查（不是 username） */
    @Override
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        UmsUser user = umsUserMapper.selectOne(
                Wrappers.<UmsUser>lambdaQuery().eq(UmsUser::getPhone, phone));
        if (user == null) {
            // 会被 DaoAuthenticationProvider 转成 BadCredentialsException，避免手机号枚举
            throw new UsernameNotFoundException("会员不存在：" + phone);
        }
        return new AppLoginUser(user);
    }

    /**
     * 按 ID 从数据库装载登录主体。
     * <p><b>本类【不】实现 {@code AppLoginUserService}</b>：那个接口的 Bean 现在是
     * {@link AppLoginUserCache}（带 Redis 缓存）。如果两个类都实现它，
     * 注入时会出现「候选 Bean 不唯一」，只能靠 {@code @Primary} 打补丁 ——
     * 不如让职责清晰：<b>本类只管「从库读」，缓存层负责「先读缓存、miss 再调本类」。</b>
     */
    public AppLoginUser loadByUserId(Long userId) {
        UmsUser user = umsUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.TOKEN_INVALID, "会员不存在或已被删除");
        }
        return new AppLoginUser(user);
    }
}
