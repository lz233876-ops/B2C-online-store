package com.shopping.service.cart.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.constant.CommonConstants;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.OmsCartItemMapper;
import com.shopping.dao.mapper.PmsSkuMapper;
import com.shopping.dao.mapper.PmsSpuMapper;
import com.shopping.model.dto.CartAddDTO;
import com.shopping.model.dto.CartSelectDTO;
import com.shopping.model.dto.CartUpdateDTO;
import com.shopping.model.entity.OmsCartItem;
import com.shopping.model.entity.PmsSku;
import com.shopping.model.entity.PmsSpu;
import com.shopping.model.vo.CartItemVO;
import com.shopping.model.vo.CartVO;
import com.shopping.service.auth.security.SecurityUtils;
import com.shopping.service.cart.CartService;
import com.shopping.service.inventory.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    /** 单个 SKU 的购买数量上限（与 CartAddDTO 上的 @Max 保持一致） */
    private static final int MAX_QUANTITY_PER_SKU = 200;
    /** 购物车最多容纳多少个不同 SKU。防止恶意用户刷满购物车拖慢列表查询 */
    private static final int MAX_CART_ITEMS = 100;

    private static final int SELECTED = 1;
    private static final int NOT_SELECTED = 0;
    private static final int SPU_ON_SALE = 1;
    private static final int SPU_PUBLISHED = 2;

    private final OmsCartItemMapper cartItemMapper;
    private final PmsSkuMapper skuMapper;
    private final PmsSpuMapper spuMapper;
    private final StockService stockService;

    @Override
    public CartVO listOfCurrentUser() {
        Long userId = SecurityUtils.getAppUserId();
        List<OmsCartItem> items = cartItemMapper.selectList(Wrappers.<OmsCartItem>lambdaQuery()
                .eq(OmsCartItem::getUserId, userId)
                .orderByDesc(OmsCartItem::getId));
        if (items.isEmpty()) {
            CartVO empty = new CartVO();
            empty.setTotalCount(0);
            empty.setTotalQuantity(0);
            empty.setSelectedCount(0);
            empty.setSelectedQuantity(0);
            empty.setSelectedAmount(0L);
            empty.setInvalidCount(0);
            return empty;
        }

        // 批量查实时 SKU / SPU / 库存，避免逐行查询（N+1）
        List<Long> skuIds = items.stream().map(OmsCartItem::getSkuId).distinct().toList();
        List<Long> spuIds = items.stream().map(OmsCartItem::getSpuId).distinct().toList();
        Map<Long, PmsSku> skuMap = skuMapper.selectBatchIds(skuIds).stream()
                .collect(java.util.stream.Collectors.toMap(PmsSku::getId, s -> s, (a, b) -> a));
        Map<Long, PmsSpu> spuMap = spuMapper.selectBatchIds(spuIds).stream()
                .collect(java.util.stream.Collectors.toMap(PmsSpu::getId, s -> s, (a, b) -> a));
        Map<Long, Integer> stockMap = stockService.getAvailableStocks(skuIds);

        List<CartItemVO> vos = new ArrayList<>(items.size());
        int totalQuantity = 0;
        int selectedCount = 0;
        int selectedQuantity = 0;
        long selectedAmount = 0L;
        int invalidCount = 0;

        for (OmsCartItem item : items) {
            CartItemVO vo = toVO(item, skuMap.get(item.getSkuId()), spuMap.get(item.getSpuId()),
                    stockMap.getOrDefault(item.getSkuId(), 0));
            vos.add(vo);
            totalQuantity += vo.getQuantity();
            if (Boolean.TRUE.equals(vo.getInvalid())) {
                invalidCount++;
            }
            // 汇总只算「已勾选且有效」的项 —— 用户结算的就是这些
            if (vo.getSelected() != null && vo.getSelected() == SELECTED && !Boolean.TRUE.equals(vo.getInvalid())) {
                selectedCount++;
                selectedQuantity += vo.getQuantity();
                selectedAmount += vo.getAmount() == null ? 0L : vo.getAmount();
            }
        }

        CartVO cart = new CartVO();
        cart.setItems(vos);
        cart.setTotalCount(items.size());
        cart.setTotalQuantity(totalQuantity);
        cart.setSelectedCount(selectedCount);
        cart.setSelectedQuantity(selectedQuantity);
        cart.setSelectedAmount(selectedAmount);
        cart.setInvalidCount(invalidCount);
        return cart;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(CartAddDTO dto) {
        Long userId = SecurityUtils.getAppUserId();
        int addQuantity = dto.getQuantity();

        PmsSku sku = skuMapper.selectById(dto.getSkuId());
        if (sku == null) {
            throw new BizException(ResultCode.SKU_NOT_FOUND);
        }
        if (sku.getStatus() == null || sku.getStatus() != 1) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND, "该规格已下架");
        }
        // 加购时就校验「商品是否在售」：否则用户能把一个已下架的商品加进购物车，
        // 到结算时才失败，体验很差
        PmsSpu spu = spuMapper.selectById(sku.getSpuId());
        if (spu == null || !Objects.equals(spu.getStatus(), SPU_ON_SALE)
                || !Objects.equals(spu.getPublishStatus(), SPU_PUBLISHED)) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND, "商品已下架");
        }

        OmsCartItem existing = cartItemMapper.selectOne(Wrappers.<OmsCartItem>lambdaQuery()
                .eq(OmsCartItem::getUserId, userId)
                .eq(OmsCartItem::getSkuId, dto.getSkuId()));

        // 目标数量：已存在则累加
        int targetQuantity = (existing == null ? 0 : existing.getQuantity()) + addQuantity;
        if (targetQuantity > MAX_QUANTITY_PER_SKU) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "单个商品最多购买 " + MAX_QUANTITY_PER_SKU + " 件（购物车已有 "
                            + (existing == null ? 0 : existing.getQuantity()) + " 件）");
        }
        // 加购就提示库存，而不是等到下单才说没货
        int available = stockService.getAvailableStock(dto.getSkuId());
        if (available <= 0) {
            throw new BizException(ResultCode.STOCK_NOT_ENOUGH, "该规格已售罄");
        }
        if (targetQuantity > available) {
            throw new BizException(ResultCode.STOCK_NOT_ENOUGH,
                    "库存不足，当前可售 " + available + " 件");
        }

        if (existing != null) {
            OmsCartItem update = new OmsCartItem();
            update.setId(existing.getId());
            update.setQuantity(targetQuantity);
            // 同时刷新快照：商品可能已经改价改名，用户再次加购说明关注的是当前信息
            update.setPrice(sku.getPrice());
            update.setSkuName(sku.getSkuName());
            update.setSkuImage(sku.getImage());
            update.setSpecDesc(sku.getSpecDesc());
            update.setSpuStatus(spu.getStatus());
            cartItemMapper.updateById(update);
            return existing.getId();
        }

        Long count = cartItemMapper.selectCount(
                Wrappers.<OmsCartItem>lambdaQuery().eq(OmsCartItem::getUserId, userId));
        if (count != null && count >= MAX_CART_ITEMS) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "购物车最多放 " + MAX_CART_ITEMS + " 种商品，请先清理");
        }

        OmsCartItem entity = new OmsCartItem();
        entity.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        entity.setUserId(userId);
        entity.setSpuId(sku.getSpuId());
        entity.setSkuId(sku.getId());
        entity.setQuantity(addQuantity);
        entity.setSelected(SELECTED);   // 新加购默认勾选，符合用户预期
        // 快照：商品后续改名改价也不影响购物车展示的稳定性
        entity.setPrice(sku.getPrice());
        entity.setSkuName(sku.getSkuName());
        entity.setSkuImage(sku.getImage());
        entity.setSpecDesc(sku.getSpecDesc());
        entity.setSpuStatus(spu.getStatus());
        cartItemMapper.insert(entity);
        log.info("加入购物车 | userId={} | skuId={} | quantity={}", userId, sku.getId(), addQuantity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(CartUpdateDTO dto) {
        OmsCartItem item = requireOwnedItem(dto.getId());
        if (dto.getQuantity() == 0) {
            // 数量改成 0 等价于删除，符合前端「减到 0 就移除」的交互
            cartItemMapper.physicalDeleteByIds(item.getUserId(), List.of(dto.getId()));
            return;
        }
        int available = stockService.getAvailableStock(item.getSkuId());
        if (dto.getQuantity() > available) {
            throw new BizException(ResultCode.STOCK_NOT_ENOUGH,
                    "库存不足，当前可售 " + available + " 件");
        }
        OmsCartItem update = new OmsCartItem();
        update.setId(dto.getId());
        update.setQuantity(dto.getQuantity());
        cartItemMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSelected(CartSelectDTO dto) {
        Long userId = SecurityUtils.getAppUserId();
        OmsCartItem update = new OmsCartItem();
        update.setSelected(dto.getSelected());

        var wrapper = Wrappers.<OmsCartItem>lambdaUpdate().eq(OmsCartItem::getUserId, userId);
        // ids 为空 = 全部；非空时还要带上 user_id 条件（防越权）
        if (!CollectionUtils.isEmpty(dto.getIds())) {
            wrapper.in(OmsCartItem::getId, dto.getIds());
        }
        cartItemMapper.update(update, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        Long userId = SecurityUtils.getAppUserId();
        // 去重，避免同一 id 传多次
        Set<Long> distinct = new LinkedHashSet<>(ids);
        cartItemMapper.physicalDeleteByIds(userId, new ArrayList<>(distinct));
    }

    @Override
    public int countOfCurrentUser() {
        Long count = cartItemMapper.selectCount(Wrappers.<OmsCartItem>lambdaQuery()
                .eq(OmsCartItem::getUserId, SecurityUtils.getAppUserId()));
        return count == null ? 0 : count.intValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearBySkuIds(Long userId, Collection<Long> skuIds) {
        if (userId == null || CollectionUtils.isEmpty(skuIds)) {
            return;
        }
        int removed = cartItemMapper.physicalDeleteBySkuIds(userId, new ArrayList<>(new LinkedHashSet<>(skuIds)));
        log.info("下单后清理购物车 | userId={} | 清理 {} 项", userId, removed);
    }

    /* ---------------- 内部方法 ---------------- */

    /** 查购物车项并校验归属：user_id 直接进 WHERE，而不是「查出来再比对」 */
    private OmsCartItem requireOwnedItem(Long id) {
        Long userId = SecurityUtils.getAppUserId();
        OmsCartItem item = cartItemMapper.selectOne(Wrappers.<OmsCartItem>lambdaQuery()
                .eq(OmsCartItem::getId, id)
                .eq(OmsCartItem::getUserId, userId));
        if (item == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "购物车项不存在");
        }
        return item;
    }

    /**
     * 组装购物车项视图，并顺带算出「是否失效」。
     * <p>失效判断放在服务端而不是前端：前端拿不到库存的权威值，
     * 而且「什么算失效」是业务规则，应该只有一处定义。
     */
    private static CartItemVO toVO(OmsCartItem item, PmsSku sku, PmsSpu spu, int available) {
        CartItemVO vo = new CartItemVO();
        vo.setId(item.getId());
        vo.setSpuId(item.getSpuId());
        vo.setSkuId(item.getSkuId());
        // 名称/图片/规格优先用购物车快照（保证展示稳定）
        vo.setSkuName(item.getSkuName());
        vo.setSkuImage(item.getSkuImage());
        vo.setSpecDesc(item.getSpecDesc());
        vo.setQuantity(item.getQuantity());
        vo.setSelected(item.getSelected());
        vo.setPrice(item.getPrice());
        vo.setAvailableStock(available);

        // 价格用【实时价】结算，快照只作展示参考
        Long currentPrice = sku == null ? item.getPrice() : sku.getPrice();
        vo.setCurrentPrice(currentPrice);
        vo.setAmount(currentPrice == null ? 0L : currentPrice * item.getQuantity());

        String reason = null;
        if (sku == null) {
            reason = "商品已被删除";
        } else if (spu == null) {
            reason = "商品已被删除";
        } else if (!Objects.equals(spu.getStatus(), SPU_ON_SALE)
                || !Objects.equals(spu.getPublishStatus(), SPU_PUBLISHED)) {
            reason = "商品已下架";
        } else if (!Objects.equals(sku.getStatus(), 1)) {
            reason = "该规格已下架";
        } else if (available <= 0) {
            reason = "已售罄";
        } else if (available < item.getQuantity()) {
            reason = "库存不足，仅剩 " + available + " 件";
        }
        vo.setInvalid(reason != null);
        vo.setInvalidReason(reason);
        return vo;
    }
}
