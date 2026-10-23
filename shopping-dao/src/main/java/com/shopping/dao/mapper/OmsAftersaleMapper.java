package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.OmsAftersale;

/**
 * 售后 Mapper。
 * <p>状态流转用 {@code update(entity, Wrappers.lambdaUpdate().eq(id).eq(status, 期望值))} 完成 ——
 * 效果与手写 XML 的 {@code WHERE status = ?} 完全一致：
 * <b>只有当前状态等于期望值时才更新</b>，受影响行数为 0 即表示状态已被其它请求改过。
 * 这里不加 XML 是为了少一份需要同步维护的 SQL。
 */
public interface OmsAftersaleMapper extends BaseMapper<OmsAftersale> {
}
