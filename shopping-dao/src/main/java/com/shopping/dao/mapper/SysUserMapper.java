package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.SysUser;

/**
 * 后台用户 Mapper。
 * <p>
 * 单表增删改查全部由 {@code BaseMapper} 提供，<b>不写任何方法</b>。
 * 业务层用 {@code LambdaQueryWrapper} 组合查询条件——好处是字段名走方法引用，
 * 重构时 IDE 能帮你改，写错字段名直接编译失败。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}
