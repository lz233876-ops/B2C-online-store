package com.shopping.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.PageResult;
import com.shopping.common.result.ResultCode;
import com.shopping.common.util.MaskUtils;
import com.shopping.dao.mapper.SysUserMapper;
import com.shopping.model.entity.SysUser;
import com.shopping.model.query.SysUserQuery;
import com.shopping.model.vo.SysUserVO;
import com.shopping.service.system.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台用户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;

    @Override
    public SysUser getByUsername(String username) {
        return sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
    }

    @Override
    public SysUser getRequiredById(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在或已被删除");
        }
        return user;
    }

    @Override
    public void updateLoginInfo(Long userId, String clientIp) {
        try {
            SysUser update = new SysUser();
            update.setId(userId);
            update.setLastLoginTime(LocalDateTime.now());
            update.setLastLoginIp(clientIp);
            // updateById 只更新非 null 字段，所以只会 SET 这两列；
            // update_time 由数据库的 ON UPDATE CURRENT_TIMESTAMP 维护。
            sysUserMapper.updateById(update);
        } catch (Exception e) {
            // 审计信息写失败不能阻断登录。记 warn 即可，不要往上抛。
            log.warn("更新登录信息失败 | userId={} | ip={}", userId, clientIp, e);
        }
    }

    @Override
    public PageResult<SysUserVO> page(SysUserQuery query) {
        // Page 的第 1、2 个参数是 current 和 size。
        // PageQuery 的 getter 已经夹住了 size 上限（<=100），
        // 而 MyBatis-Plus 的分页插件又配了 maxLimit(100) —— 两层防护。
        Page<SysUser> page = new Page<>(query.getCurrent(), query.getSize());

        // 条件构造器的第一个 boolean 参数是「是否拼接该条件」。
        // 用它可以避免写一堆 if (xxx != null) { wrapper.eq(...) }，
        // 而且字段名走方法引用（SysUser::getUsername），改字段名时编译期就能发现。
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getNickname()), SysUser::getNickname, query.getNickname())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getCreateTime);

        Page<SysUser> result = sysUserMapper.selectPage(page, wrapper);

        List<SysUserVO> records = result.getRecords().stream()
                .map(SysUserServiceImpl::toVO)
                .toList();

        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    /**
     * 实体 → VO。
     * <p>
     * 手写而不是用 MapStruct：这里同时要做<b>脱敏</b>和<b>字段裁剪</b>
     * （丢掉 password），是两个方向的操作，用映射框架反而更难读。
     * 字段一多、纯搬运且无需加工时，才值得上 MapStruct。
     */
    private static SysUserVO toVO(SysUser user) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRealName(user.getRealName());
        // 出口脱敏：内层拿到真实值才能做业务，最外层才遮盖
        vo.setPhone(MaskUtils.phone(user.getPhone()));
        vo.setEmail(MaskUtils.email(user.getEmail()));
        vo.setGender(user.getGender());
        vo.setDeptName(user.getDeptName());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setLastLoginIp(user.getLastLoginIp());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
