package com.shopping.service.product;

import com.shopping.common.result.PageResult;
import com.shopping.model.dto.AttributeDTO;
import com.shopping.model.query.AttributeQuery;
import com.shopping.model.vo.AttributeVO;

import java.util.List;

/**
 * 商品属性服务。
 */
public interface AttributeService {

    /** 分页查询属性（含各自的属性值） */
    PageResult<AttributeVO> page(AttributeQuery query);

    /** 属性详情（含属性值） */
    AttributeVO detail(Long id);

    /**
     * 查某个类目下的<b>规格属性</b>（{@code type=1}）及其属性值。
     * <p>商品表单要用它来渲染「规格」区域并计算 SKU 笛卡尔积。
     */
    List<AttributeVO> listSpecByCategory(Long categoryId);

    /** 新增属性（含属性值），聚合保存 */
    Long create(AttributeDTO dto);

    /** 修改属性（含属性值增删改），聚合保存 */
    void update(AttributeDTO dto);

    /** 删除属性；被商品或 SKU 引用时拒绝 */
    void delete(Long id);
}
