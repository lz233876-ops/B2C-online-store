package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.SysRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色 Mapper。
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查某个用户拥有的<b>启用状态</b>角色编码。
     * <p>
     * 三表关联（{@code sys_role} + {@code sys_user_role}），单表 CRUD 表达不了，
     * 因此走手写 XML（见 {@code resources/mapper/SysRoleMapper.xml}）。
     *
     * @param userId 用户 ID
     * @return 角色编码列表，如 {@code [SUPER_ADMIN]}；无角色时返回空列表，不返回 null
     */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
