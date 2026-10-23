package com.shopping.service.product.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.PageResult;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.PmsBrandMapper;
import com.shopping.dao.mapper.PmsCategoryMapper;
import com.shopping.dao.mapper.PmsSkuMapper;
import com.shopping.dao.mapper.PmsSpuMapper;
import com.shopping.model.entity.PmsBrand;
import com.shopping.model.entity.PmsCategory;
import com.shopping.model.entity.PmsSku;
import com.shopping.model.entity.PmsSpu;
import com.shopping.model.vo.AppSkuVO;
import com.shopping.model.vo.AppSpuDetailVO;
import com.shopping.model.vo.AppSpuVO;
import com.shopping.service.inventory.StockService;
import com.shopping.service.product.AppProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppProductServiceImpl implements AppProductService {

    private static final int PUBLISH_PASSED = 2;
    private static final int ON_SALE = 1;

    private final PmsSpuMapper spuMapper;
    private final PmsSkuMapper skuMapper;
    private final PmsBrandMapper brandMapper;
    private final PmsCategoryMapper categoryMapper;
    private final StockService stockService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<AppSpuVO> page(int pageNum, int pageSize, Long categoryId, String keyword) {
        var wrapper = Wrappers.<PmsSpu>lambdaQuery()
                // ⚠️ 可见性是 C 端的第一道闸门：草稿、待审、已驳回、已下架的商品一律不出现
                .eq(PmsSpu::getPublishStatus, PUBLISH_PASSED)
                .eq(PmsSpu::getStatus, ON_SALE)
                .eq(categoryId != null, PmsSpu::getCategoryId, categoryId)
                .like(StringUtils.hasText(keyword), PmsSpu::getSpuName, keyword)
                .orderByDesc(PmsSpu::getSort)
                .orderByDesc(PmsSpu::getId);

        var result = spuMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<PmsSpu> spus = result.getRecords();
        if (spus.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        // 批量取 SKU（算可售库存与 SKU 数量）与品牌/类目名，避免 N+1
        List<Long> spuIds = spus.stream().map(PmsSpu::getId).toList();
        List<PmsSku> allSkus = skuMapper.selectList(Wrappers.<PmsSku>lambdaQuery()
                .in(PmsSku::getSpuId, spuIds)
                .eq(PmsSku::getStatus, 1));
        Map<Long, List<PmsSku>> skuBySpu = allSkus.stream()
                .collect(Collectors.groupingBy(PmsSku::getSpuId));
        List<Long> skuIds = allSkus.stream().map(PmsSku::getId).toList();
        Map<Long, Integer> stockMap = stockService.getAvailableStocks(skuIds);

        Map<Long, String> brandNames = nameMapOfBrands(spus);
        Map<Long, String> categoryNames = nameMapOfCategories(spus);

        List<AppSpuVO> vos = new ArrayList<>(spus.size());
        for (PmsSpu spu : spus) {
            AppSpuVO vo = new AppSpuVO();
            vo.setId(spu.getId());
            vo.setSpuName(spu.getSpuName());
            vo.setSubTitle(spu.getSubTitle());
            vo.setMainImage(spu.getMainImage());
            vo.setPrice(spu.getPrice());
            vo.setPriceYuan(yuan(spu.getPrice()));
            vo.setOriginalPrice(spu.getOriginalPrice());
            vo.setOriginalPriceYuan(yuan(spu.getOriginalPrice()));
            vo.setSaleCount(spu.getSaleCount());
            vo.setIsNew(spu.getIsNew());
            vo.setIsRecommend(spu.getIsRecommend());
            vo.setBrandName(brandNames.get(spu.getBrandId()));
            vo.setCategoryName(categoryNames.get(spu.getCategoryId()));

            List<PmsSku> skus = skuBySpu.getOrDefault(spu.getId(), List.of());
            vo.setSkuCount(skus.size());
            // 可售库存以【权威库存】为准（wms_stock 的生成列），不用 pms_sku.stock 冗余字段
            vo.setAvailableStock(skus.stream()
                    .mapToInt(s -> stockMap.getOrDefault(s.getId(), 0))
                    .sum());
            vos.add(vo);
        }
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), vos);
    }

    @Override
    public AppSpuDetailVO detail(Long spuId) {
        PmsSpu spu = spuMapper.selectById(spuId);
        if (spu == null
                || !Objects.equals(spu.getPublishStatus(), PUBLISH_PASSED)
                || !Objects.equals(spu.getStatus(), ON_SALE)) {
            // 统一返回「商品不存在或已下架」：不区分「不存在」与「未上架」，
            // 避免通过错误差异探测未上架商品的存在性
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }

        AppSpuDetailVO vo = new AppSpuDetailVO();
        vo.setId(spu.getId());
        vo.setSpuName(spu.getSpuName());
        vo.setSubTitle(spu.getSubTitle());
        vo.setMainImage(spu.getMainImage());
        vo.setPrice(spu.getPrice());
        vo.setPriceYuan(yuan(spu.getPrice()));
        vo.setOriginalPrice(spu.getOriginalPrice());
        vo.setOriginalPriceYuan(yuan(spu.getOriginalPrice()));
        vo.setSaleCount(spu.getSaleCount());
        vo.setIsNew(spu.getIsNew());
        vo.setIsRecommend(spu.getIsRecommend());
        vo.setBrandName(nameMapOfBrands(List.of(spu)).get(spu.getBrandId()));
        vo.setCategoryName(nameMapOfCategories(List.of(spu)).get(spu.getCategoryId()));
        vo.setDetailHtml(spu.getDetailHtml());
        vo.setAlbumImages(parseImages(spu.getAlbumImages()));

        List<PmsSku> skus = skuMapper.selectList(Wrappers.<PmsSku>lambdaQuery()
                .eq(PmsSku::getSpuId, spu.getId())
                .eq(PmsSku::getStatus, 1)
                .orderByAsc(PmsSku::getPrice));
        vo.setSkuCount(skus.size());
        Map<Long, Integer> stockMap = stockService.getAvailableStocks(
                skus.stream().map(PmsSku::getId).toList());

        List<AppSkuVO> skuVos = new ArrayList<>(skus.size());
        // 规格属性聚合：从每个 SKU 的 spec_json 里收集不同的 (属性ID, 值ID)
        Map<String, AppSpuDetailVO.SpecAttribute> attrBucket = new LinkedHashMap<>();
        for (PmsSku sku : skus) {
            AppSkuVO sv = new AppSkuVO();
            sv.setId(sku.getId());
            sv.setSkuName(sku.getSkuName());
            sv.setSpecDesc(sku.getSpecDesc());
            sv.setImage(StringUtils.hasText(sku.getImage()) ? sku.getImage() : spu.getMainImage());
            sv.setPrice(sku.getPrice());
            sv.setPriceYuan(yuan(sku.getPrice()));
            sv.setOriginalPrice(sku.getOriginalPrice());
            sv.setAvailableStock(stockMap.getOrDefault(sku.getId(), 0));

            List<AppSkuVO.SpecValue> specValues = parseSpec(sku.getSpecJson());
            sv.setSpecValues(specValues);
            skuVos.add(sv);

            for (AppSkuVO.SpecValue pv : specValues) {
                AppSpuDetailVO.SpecAttribute attr = attrBucket.computeIfAbsent(
                        String.valueOf(pv.getAttributeId()), k -> {
                            AppSpuDetailVO.SpecAttribute a = new AppSpuDetailVO.SpecAttribute();
                            a.setAttributeId(pv.getAttributeId());
                            a.setAttributeName(pv.getAttributeName());
                            return a;
                        });
                boolean exists = attr.getValues().stream()
                        .anyMatch(v -> Objects.equals(v.getValueId(), pv.getValueId()));
                if (!exists) {
                    AppSpuDetailVO.SpecValueItem item = new AppSpuDetailVO.SpecValueItem();
                    item.setValueId(pv.getValueId());
                    item.setValueName(pv.getValueName());
                    attr.getValues().add(item);
                }
            }
        }
        vo.setSkus(skuVos);
        vo.setSpecAttributes(new ArrayList<>(attrBucket.values()));
        vo.setAvailableStock(skuVos.stream().mapToInt(AppSkuVO::getAvailableStock).sum());
        return vo;
    }

    /* ---------------- 内部方法 ---------------- */

    private Map<Long, String> nameMapOfBrands(List<PmsSpu> spus) {
        List<Long> ids = spus.stream().map(PmsSpu::getBrandId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return brandMapper.selectBatchIds(ids).stream()
                // 品牌展示用中文名（nameCn），没有则退回英文名（name）
                .collect(Collectors.toMap(PmsBrand::getId,
                        b -> StringUtils.hasText(b.getNameCn()) ? b.getNameCn() : b.getName(),
                        (a, b) -> a));
    }

    private Map<Long, String> nameMapOfCategories(List<PmsSpu> spus) {
        List<Long> ids = spus.stream().map(PmsSpu::getCategoryId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(PmsCategory::getId, PmsCategory::getName, (a, b) -> a));
    }

    private List<String> parseImages(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            log.warn("相册图片解析失败 | json={}", json);
            return new ArrayList<>();
        }
    }

    private List<AppSkuVO.SpecValue> parseSpec(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AppSkuVO.SpecValue>>() { });
        } catch (Exception e) {
            log.warn("SKU 规格解析失败 | json={}", json);
            return new ArrayList<>();
        }
    }

    private static String yuan(Long fen) {
        if (fen == null) {
            return "0.00";
        }
        return BigDecimal.valueOf(fen).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }
}
