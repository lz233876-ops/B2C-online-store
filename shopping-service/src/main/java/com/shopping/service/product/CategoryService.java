package com.shopping.service.product;

import com.shopping.model.dto.CategoryDTO;
import com.shopping.model.vo.CategoryTreeVO;

import java.util.List;

/**
 * 商品类目服务。
 */
public interface CategoryService {

    /**
     * 类目树。
     *
     * @param status 为 null 时返回全部（后台管理用），传 1 只返回启用的（前台用）
     */
    List<CategoryTreeVO> tree(Integer status);

    /** 新增类目，返回新 ID。{@code level} 由父类目派生，不采信入参 */
    Long create(CategoryDTO dto);

    /** 修改类目。会校验「不能移动到自己的子孙节点下」 */
    void update(CategoryDTO dto);

    /**
     * 删除类目。
     * <p>有子类目或有商品时拒绝——静默级联删除会毁掉商品数据。
     */
    void delete(Long id);
}
