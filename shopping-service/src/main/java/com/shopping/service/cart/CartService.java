package com.shopping.service.cart;

import com.shopping.model.dto.CartAddDTO;
import com.shopping.model.dto.CartSelectDTO;
import com.shopping.model.dto.CartUpdateDTO;
import com.shopping.model.vo.CartVO;

import java.util.Collection;
import java.util.List;

/**
 * 购物车服务。
 * <p>
 * 所有方法都以「当前登录会员」为作用域，<b>入参里没有 userId</b>——
 * 这是水平越权防线的一部分（见 {@code docs/03} 安全设计）。
 */
public interface CartService {

    /** 购物车列表 + 汇总（含失效项标记） */
    CartVO listOfCurrentUser();

    /** 加入购物车；同一 SKU 重复加购会累加数量 */
    Long add(CartAddDTO dto);

    /** 修改数量；数量传 0 表示删除该项 */
    void updateQuantity(CartUpdateDTO dto);

    /** 勾选/取消勾选；{@code ids} 为空表示全部 */
    void updateSelected(CartSelectDTO dto);

    /** 批量删除 */
    void remove(List<Long> ids);

    /** 当前会员购物车里的 SKU 数量（用于限制购物车条目数） */
    int countOfCurrentUser();

    /**
     * 按 SKU 清理购物车项 —— 下单成功后调用。
     * <p>显式传 {@code userId} 而不是从登录态取：下单链路将来可能被 MQ 消费者复用，
     * 那时没有请求上下文。让调用方负责提供身份，比在方法内部隐式依赖线程上下文更可靠。
     */
    void clearBySkuIds(Long userId, Collection<Long> skuIds);
}
