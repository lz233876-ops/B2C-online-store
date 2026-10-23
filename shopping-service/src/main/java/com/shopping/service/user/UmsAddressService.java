package com.shopping.service.user;

import com.shopping.model.dto.AddressDTO;
import com.shopping.model.vo.AddressVO;

import java.util.List;

/**
 * 收货地址服务。
 * <p>
 * ⚠️ <b>本服务的每一个方法都必须校验「地址属于当前登录会员」。</b>
 * 这是<b>水平越权</b>（IDOR）的典型防线：如果不校验，
 * 攻击者把 URL 里的 {@code id} 从 100 改成 101，就能查到/改到别人的收货地址与手机号。
 * 这是电商类应用最常见的高危漏洞之一，也是面试官很爱问的点。
 * <p>
 * 实现方式：用户 ID <b>一律从登录态取</b>（{@code SecurityUtils.getAppUserId()}），
 * 并且查询时把 {@code user_id} 作为条件之一，而不是「先查出来再比对」——
 * 后者容易在某个分支忘记比对。
 */
public interface UmsAddressService {

    /** 当前会员的地址列表，默认地址排在最前 */
    List<AddressVO> listOfCurrentUser();

    /** 查地址详情；不属于当前会员时返回「地址不存在」而不是「无权限」（避免探测） */
    AddressVO detail(Long id);

    /** 新增地址，返回新 ID。第一条地址会自动成为默认地址 */
    Long create(AddressDTO dto);

    void update(AddressDTO dto);

    void delete(Long id);

    /** 设为默认地址（事务内先把其它地址置为非默认） */
    void setDefault(Long id);
}
