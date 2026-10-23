package com.shopping.service.product;

import com.shopping.common.result.PageResult;
import com.shopping.model.vo.AppSpuDetailVO;
import com.shopping.model.vo.AppSpuVO;

/**
 * C 端商品浏览服务。
 * <p>
 * 与后台 {@code SpuService} <b>刻意分开</b>：
 * <ul>
 *   <li><b>数据边界不同</b>：C 端绝不能出现成本价（见 {@code AppSpuVO} 的说明）；</li>
 *   <li><b>可见性不同</b>：C 端只能看到「审核通过 + 已上架」的商品，后台能看到草稿；</li>
 *   <li><b>排序与字段不同</b>：C 端按运营排序 + 销量，且只返回展示需要的字段。</li>
 * </ul>
 * 把两者合成一个方法、靠参数区分「是不是 C 端」，是这类越权/信息泄露最常见的来源。
 */
public interface AppProductService {

    /**
     * 商品分页（仅「审核通过 + 已上架」）。
     *
     * @param categoryId 类目筛选，为空不过滤
     * @param keyword    商品名模糊搜索，为空不过滤
     */
    PageResult<AppSpuVO> page(int pageNum, int pageSize, Long categoryId, String keyword);

    /**
     * 商品详情（含 SKU、规格属性、相册、详情）。
     *
     * @throws com.shopping.common.exception.BizException 商品不存在或未上架时抛 {@code PRODUCT_NOT_FOUND}
     */
    AppSpuDetailVO detail(Long spuId);
}
