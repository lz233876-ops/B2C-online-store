package com.shopping.service.product;

import com.shopping.common.result.PageResult;
import com.shopping.model.dto.SpuSaveDTO;
import com.shopping.model.query.SpuQuery;
import com.shopping.model.vo.SpuDetailVO;
import com.shopping.model.vo.SpuVO;

/**
 * SPU 商品服务（后台管理端）。
 */
public interface SpuService {

    PageResult<SpuVO> page(SpuQuery query);

    SpuDetailVO detail(Long id);

    /**
     * 聚合保存（新增或修改）。
     * <p>
     * 一个事务内完成：SPU 主体 + 规格/参数属性 + 图片 + SKU（含规格组合、规格快照）
     * + 权威库存 {@code wms_stock} + 冗余汇总字段重算。
     *
     * @return SPU ID
     */
    Long save(SpuSaveDTO dto);

    /**
     * 上下架。
     * <p>⚠️ 改的是 {@code pms_spu.status}（1上架/0下架），<b>不是</b> {@code publish_status}（审核状态）。
     *
     * @param status 1 上架，0 下架
     */
    void updateStatus(Long id, Integer status);

    /** 删除商品（逻辑删除，连带下架其 SKU） */
    void delete(Long id);
}
