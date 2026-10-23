package com.shopping.service.system;

import com.shopping.common.result.PageResult;
import com.shopping.model.entity.SysUser;
import com.shopping.model.query.SysUserQuery;
import com.shopping.model.vo.SysUserVO;

/**
 * 后台用户服务。
 * <p>
 * Phase 1 先提供认证所需的最小能力 + 列表查询，
 * 完整的增删改、分配角色在系统管理模块补全。
 */
public interface SysUserService {

    /**
     * 按登录账号查询，不存在返回 {@code null}。
     */
    SysUser getByUsername(String username);

    /**
     * 按 ID 查询，不存在则抛业务异常。
     */
    SysUser getRequiredById(Long userId);

    /**
     * 更新最后登录时间与 IP。
     * <p>
     * 刻意做成「失败也不影响登录」：登录时间属于审计信息，
     * 写失败不该让用户登不进来。所以调用方要容错，本方法自身不抛异常。
     */
    void updateLoginInfo(Long userId, String clientIp);

    /**
     * 分页查询后台用户。
     * <p>
     * 返回 VO 而不是实体：密码等敏感字段绝不离开 Service 层，
     * 且手机号/邮箱在此处完成脱敏。
     */
    PageResult<SysUserVO> page(SysUserQuery query);
}
