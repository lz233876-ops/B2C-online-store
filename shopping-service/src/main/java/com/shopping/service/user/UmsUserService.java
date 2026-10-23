package com.shopping.service.user;

import com.shopping.model.dto.AppRegisterDTO;
import com.shopping.model.entity.UmsUser;

/**
 * C 端会员服务。
 */
public interface UmsUserService {

    /** 按手机号查询（登录主标识），不存在返回 {@code null} */
    UmsUser getByPhone(String phone);

    /** 按 ID 查询，不存在抛业务异常 */
    UmsUser getRequiredById(Long userId);

    /**
     * 注册会员。
     * <p>手机号已存在时抛 {@code PHONE_ALREADY_REGISTERED}——
     * 这里给明确提示而不是含糊带过，因为注册场景用户需要知道「去登录」。
     * （登录场景则相反，必须模糊化以< b>避免手机号枚举</b>，见 {@code UmsUserDetailsService}。）
     *
     * @param clientIp 客户端 IP，仅用于审计
     * @return 新会员 ID
     */
    Long register(AppRegisterDTO dto, String clientIp);

    /** 更新最后登录时间与 IP；失败不影响登录 */
    void updateLoginInfo(Long userId, String clientIp);
}
