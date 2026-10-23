package com.shopping.service.user.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.common.util.MaskUtils;
import com.shopping.dao.mapper.UmsUserMapper;
import com.shopping.model.dto.AppRegisterDTO;
import com.shopping.model.entity.UmsUser;
import com.shopping.service.user.UmsUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UmsUserServiceImpl implements UmsUserService {

    /** 新会员默认等级：1 = 普通会员（见 docs/04 §8.4） */
    private static final long DEFAULT_MEMBER_LEVEL_ID = 1L;

    private final UmsUserMapper umsUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UmsUser getByPhone(String phone) {
        return umsUserMapper.selectOne(Wrappers.<UmsUser>lambdaQuery().eq(UmsUser::getPhone, phone));
    }

    @Override
    public UmsUser getRequiredById(Long userId) {
        UmsUser user = umsUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND);
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(AppRegisterDTO dto, String clientIp) {
        String phone = dto.getPhone().trim();
        if (getByPhone(phone) != null) {
            throw new BizException(ResultCode.PHONE_ALREADY_REGISTERED);
        }

        UmsUser user = new UmsUser();
        user.setPhone(phone);
        // 密码必须 BCrypt 加密后再落库。绝不能用 MD5/SHA（无盐、可彩虹表反查）
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        // 昵称不传就用脱敏手机号，避免出现「无名氏」影响后台排查
        user.setNickname(StringUtils.hasText(dto.getNickname())
                ? dto.getNickname().trim()
                : MaskUtils.phone(phone));
        user.setGender(0);
        user.setMemberLevelId(DEFAULT_MEMBER_LEVEL_ID);
        user.setGrowth(0);
        user.setPoints(0);
        user.setTotalPoints(0);
        user.setStatus(1);
        user.setRegisterSource(dto.getRegisterSource() == null ? 2 : dto.getRegisterSource());
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        umsUserMapper.insert(user);

        log.info("会员注册成功 | userId={} | phone={}", user.getId(), MaskUtils.phone(phone));
        return user.getId();
    }

    @Override
    public void updateLoginInfo(Long userId, String clientIp) {
        try {
            UmsUser update = new UmsUser();
            update.setId(userId);
            update.setLastLoginTime(LocalDateTime.now());
            update.setLastLoginIp(clientIp);
            umsUserMapper.updateById(update);
        } catch (Exception e) {
            // 审计信息写失败不能阻断登录
            log.warn("更新会员登录信息失败 | userId={} | ip={}", userId, clientIp, e);
        }
    }
}
