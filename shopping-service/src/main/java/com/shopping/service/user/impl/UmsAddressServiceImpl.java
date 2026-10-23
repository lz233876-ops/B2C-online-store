package com.shopping.service.user.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.common.util.MaskUtils;
import com.shopping.dao.mapper.UmsUserAddressMapper;
import com.shopping.model.dto.AddressDTO;
import com.shopping.model.entity.UmsUserAddress;
import com.shopping.model.vo.AddressVO;
import com.shopping.service.auth.security.SecurityUtils;
import com.shopping.service.user.UmsAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UmsAddressServiceImpl implements UmsAddressService {

    /** 单会员地址数量上限。防止恶意用户刷几万条地址把库撑大 */
    private static final int MAX_ADDRESS_PER_USER = 20;
    private static final int IS_DEFAULT = 1;
    private static final int NOT_DEFAULT = 0;

    private final UmsUserAddressMapper addressMapper;

    @Override
    public List<AddressVO> listOfCurrentUser() {
        Long userId = SecurityUtils.getAppUserId();
        return addressMapper.selectList(Wrappers.<UmsUserAddress>lambdaQuery()
                        .eq(UmsUserAddress::getUserId, userId)
                        // 默认地址排最前，其余按创建时间倒序（最近添加的在上面）
                        .orderByDesc(UmsUserAddress::getDefaultStatus)
                        .orderByDesc(UmsUserAddress::getId))
                .stream().map(UmsAddressServiceImpl::toVO).toList();
    }

    @Override
    public AddressVO detail(Long id) {
        return toVO(requireOwnedAddress(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AddressDTO dto) {
        Long userId = SecurityUtils.getAppUserId();

        Long count = addressMapper.selectCount(
                Wrappers.<UmsUserAddress>lambdaQuery().eq(UmsUserAddress::getUserId, userId));
        long existing = count == null ? 0 : count;
        if (existing >= MAX_ADDRESS_PER_USER) {
            throw new BizException(ResultCode.ADDRESS_LIMIT_EXCEED,
                    "最多只能保存 " + MAX_ADDRESS_PER_USER + " 条收货地址");
        }
        // 第一条地址自动设为默认：否则用户只有一条地址却没有默认地址，
        // 下单页「默认地址」为空，体验很怪
        boolean asDefault = existing == 0 || isDefaultRequested(dto);
        if (asDefault) {
            clearDefault(userId);
        }

        UmsUserAddress entity = new UmsUserAddress();
        applyDto(entity, dto);
        entity.setUserId(userId);
        entity.setDefaultStatus(asDefault ? IS_DEFAULT : NOT_DEFAULT);
        addressMapper.insert(entity);
        log.info("新增收货地址 | userId={} | addressId={} | default={}", userId, entity.getId(), asDefault);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AddressDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "修改地址时必须传 id");
        }
        UmsUserAddress existing = requireOwnedAddress(dto.getId());

        // 默认状态只能「转移」不能「取消」：如果这是当前默认地址，
        // 而请求里传了 defaultStatus=0，直接忽略——否则会出现「一个默认地址都没有」。
        boolean keepDefault = existing.getDefaultStatus() != null && existing.getDefaultStatus() == IS_DEFAULT;
        boolean setDefault = isDefaultRequested(dto) || keepDefault;
        if (setDefault && !keepDefault) {
            clearDefault(existing.getUserId());
        }

        UmsUserAddress update = new UmsUserAddress();
        update.setId(dto.getId());
        applyDto(update, dto);
        update.setDefaultStatus(setDefault ? IS_DEFAULT : NOT_DEFAULT);
        addressMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        UmsUserAddress existing = requireOwnedAddress(id);
        addressMapper.deleteById(id);
        log.info("删除收货地址 | userId={} | addressId={}", existing.getUserId(), id);

        // 删掉的若是默认地址，则把剩下最新的一条提升为默认，
        // 保证「有地址就一定有默认地址」这个不变量始终成立
        if (existing.getDefaultStatus() != null && existing.getDefaultStatus() == IS_DEFAULT) {
            List<UmsUserAddress> rest = addressMapper.selectList(Wrappers.<UmsUserAddress>lambdaQuery()
                    .eq(UmsUserAddress::getUserId, existing.getUserId())
                    .orderByDesc(UmsUserAddress::getId));
            if (!rest.isEmpty()) {
                UmsUserAddress promote = new UmsUserAddress();
                promote.setId(rest.get(0).getId());
                promote.setDefaultStatus(IS_DEFAULT);
                addressMapper.updateById(promote);
                log.info("默认地址被删除，自动提升 | userId={} | newDefaultId={}",
                        existing.getUserId(), rest.get(0).getId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        UmsUserAddress existing = requireOwnedAddress(id);
        clearDefault(existing.getUserId());

        UmsUserAddress update = new UmsUserAddress();
        update.setId(id);
        update.setDefaultStatus(IS_DEFAULT);
        addressMapper.updateById(update);
    }

    /* ---------------- 内部方法 ---------------- */

    /**
     * 查地址并校验归属。
     * <p><b>关键设计：把 {@code user_id} 直接写进 WHERE，而不是「先查出来再 if 比对」。</b>
     * 前者只要用了这个方法就天然安全；后者每加一个调用点都可能忘记比对。
     * <p>不属于当前会员时返回 {@code ADDRESS_NOT_FOUND}（而不是 403）——
     * 避免攻击者通过错误码差异探测「哪些地址 ID 真实存在」。
     */
    private UmsUserAddress requireOwnedAddress(Long id) {
        Long userId = SecurityUtils.getAppUserId();
        UmsUserAddress address = addressMapper.selectOne(Wrappers.<UmsUserAddress>lambdaQuery()
                .eq(UmsUserAddress::getId, id)
                .eq(UmsUserAddress::getUserId, userId));
        if (address == null) {
            throw new BizException(ResultCode.ADDRESS_NOT_FOUND);
        }
        return address;
    }

    private void clearDefault(Long userId) {
        UmsUserAddress reset = new UmsUserAddress();
        reset.setDefaultStatus(NOT_DEFAULT);
        addressMapper.update(reset, Wrappers.<UmsUserAddress>lambdaUpdate()
                .eq(UmsUserAddress::getUserId, userId)
                .eq(UmsUserAddress::getDefaultStatus, IS_DEFAULT));
    }

    private static boolean isDefaultRequested(AddressDTO dto) {
        return dto.getDefaultStatus() != null && dto.getDefaultStatus() == IS_DEFAULT;
    }

    private static void applyDto(UmsUserAddress entity, AddressDTO dto) {
        entity.setReceiverName(dto.getReceiverName().trim());
        entity.setReceiverPhone(dto.getReceiverPhone().trim());
        entity.setProvince(dto.getProvince());
        entity.setCity(dto.getCity());
        entity.setDistrict(dto.getDistrict());
        entity.setDetailAddress(dto.getDetailAddress());
        entity.setPostalCode(dto.getPostalCode());
        entity.setTag(dto.getTag());
    }

    private static AddressVO toVO(UmsUserAddress a) {
        AddressVO vo = new AddressVO();
        vo.setId(a.getId());
        vo.setReceiverName(a.getReceiverName());
        // 出口脱敏：收货人手机号也是隐私
        vo.setReceiverPhone(MaskUtils.phone(a.getReceiverPhone()));
        vo.setProvince(a.getProvince());
        vo.setCity(a.getCity());
        vo.setDistrict(a.getDistrict());
        vo.setDetailAddress(a.getDetailAddress());
        vo.setFullAddress(nullSafe(a.getProvince()) + nullSafe(a.getCity())
                + nullSafe(a.getDistrict()) + nullSafe(a.getDetailAddress()));
        vo.setPostalCode(a.getPostalCode());
        vo.setDefaultStatus(a.getDefaultStatus());
        vo.setTag(a.getTag());
        return vo;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
