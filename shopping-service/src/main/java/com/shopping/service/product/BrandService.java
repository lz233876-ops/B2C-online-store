package com.shopping.service.product;

import com.shopping.common.result.PageResult;
import com.shopping.model.dto.BrandDTO;
import com.shopping.model.query.BrandQuery;
import com.shopping.model.vo.BrandVO;

import java.util.List;

/**
 * 品牌服务。
 */
public interface BrandService {

    PageResult<BrandVO> page(BrandQuery query);

    /** 启用状态的品牌列表，供商品表单的下拉框使用 */
    List<BrandVO> listEnabled();

    Long create(BrandDTO dto);

    void update(BrandDTO dto);

    /** 删除品牌；有商品引用时拒绝 */
    void delete(Long id);
}
