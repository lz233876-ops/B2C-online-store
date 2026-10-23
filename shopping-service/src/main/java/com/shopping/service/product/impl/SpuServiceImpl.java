package com.shopping.service.product.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.common.constant.CommonConstants;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.PageResult;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.PmsAttributeMapper;
import com.shopping.dao.mapper.PmsAttributeValueMapper;
import com.shopping.dao.mapper.PmsBrandMapper;
import com.shopping.dao.mapper.PmsCategoryMapper;
import com.shopping.dao.mapper.PmsProductImageMapper;
import com.shopping.dao.mapper.PmsSkuAttributeValueMapper;
import com.shopping.dao.mapper.PmsSkuMapper;
import com.shopping.dao.mapper.PmsSpuAttributeMapper;
import com.shopping.dao.mapper.PmsSpuMapper;
import com.shopping.dao.mapper.WmsStockMapper;
import com.shopping.model.dto.SpuSaveDTO;
import com.shopping.model.entity.PmsAttribute;
import com.shopping.model.entity.PmsAttributeValue;
import com.shopping.model.entity.PmsBrand;
import com.shopping.model.entity.PmsCategory;
import com.shopping.model.entity.PmsProductImage;
import com.shopping.model.entity.PmsSku;
import com.shopping.model.entity.PmsSkuAttributeValue;
import com.shopping.model.entity.PmsSpu;
import com.shopping.model.entity.PmsSpuAttribute;
import com.shopping.model.entity.WmsStock;
import com.shopping.model.query.SpuQuery;
import com.shopping.model.vo.SkuVO;
import com.shopping.model.vo.SpuDetailVO;
import com.shopping.model.vo.SpuVO;
import com.shopping.service.auth.security.SecurityUtils;
import com.shopping.service.inventory.StockService;
import com.shopping.service.product.SpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SPU 商品服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpuServiceImpl implements SpuService {

    /** 规格属性 */
    private static final int ATTR_TYPE_SPEC = 1;
    /** 参数属性 */
    private static final int ATTR_TYPE_PARAM = 2;
    /** SPU 级图片在 pms_product_image.sku_id 里的哨兵值 */
    private static final long SPU_LEVEL_IMAGE = 0L;
    /** 图片类型：主图 / 轮播图 */
    private static final int IMAGE_MAIN = 1;
    private static final int IMAGE_ALBUM = 2;

    private final PmsSpuMapper spuMapper;
    private final PmsSkuMapper skuMapper;
    private final WmsStockMapper stockMapper;
    private final PmsSpuAttributeMapper spuAttributeMapper;
    private final PmsSkuAttributeValueMapper skuAttributeValueMapper;
    private final PmsProductImageMapper productImageMapper;
    private final PmsCategoryMapper categoryMapper;
    private final PmsBrandMapper brandMapper;
    private final PmsAttributeMapper attributeMapper;
    private final PmsAttributeValueMapper attributeValueMapper;
    private final StockService stockService;
    private final ObjectMapper objectMapper;

    /* ==================== 查询 ==================== */

    @Override
    public PageResult<SpuVO> page(SpuQuery query) {
        Page<PmsSpu> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<PmsSpu> wrapper = Wrappers.<PmsSpu>lambdaQuery()
                .like(StringUtils.hasText(query.getSpuName()), PmsSpu::getSpuName, query.getSpuName())
                .eq(query.getBrandId() != null, PmsSpu::getBrandId, query.getBrandId())
                .eq(query.getPublishStatus() != null, PmsSpu::getPublishStatus, query.getPublishStatus())
                .eq(query.getStatus() != null, PmsSpu::getStatus, query.getStatus())
                // 注意：pms_spu.sort 是「值越大越靠前」，与类目/品牌相反
                .orderByDesc(PmsSpu::getSort)
                .orderByDesc(PmsSpu::getId);

        if (query.getCategoryId() != null) {
            // 传父类目时要匹配它下面所有层级的商品，否则「选了一级类目查不到任何商品」
            wrapper.in(PmsSpu::getCategoryId, expandCategoryIds(query.getCategoryId()));
        }

        Page<PmsSpu> result = spuMapper.selectPage(page, wrapper);
        List<PmsSpu> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), List.of());
        }

        // 批量补名字与 SKU 数量，避免逐行查询造成的 N+1
        Map<Long, String> categoryNames = loadCategoryNames(records.stream().map(PmsSpu::getCategoryId).toList());
        Map<Long, String> brandNames = loadBrandNames(records.stream().map(PmsSpu::getBrandId).toList());
        Map<Long, Long> skuCounts = loadSkuCounts(records.stream().map(PmsSpu::getId).toList());

        List<SpuVO> vos = records.stream()
                .map(spu -> toVO(spu, categoryNames, brandNames, skuCounts))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), vos);
    }

    @Override
    public SpuDetailVO detail(Long id) {
        PmsSpu spu = requireSpu(id);
        SpuDetailVO vo = new SpuDetailVO();
        fillBase(vo, spu,
                loadCategoryNames(List.of(spu.getCategoryId())),
                loadBrandNames(spu.getBrandId() == null ? List.of() : List.of(spu.getBrandId())),
                loadSkuCounts(List.of(id)));

        vo.setKeywords(spu.getKeywords());
        vo.setUnit(spu.getUnit());
        vo.setWeight(spu.getWeight());
        vo.setDetailHtml(spu.getDetailHtml());
        vo.setAlbumImages(parseStringArray(spu.getAlbumImages()));

        // 图片
        List<PmsProductImage> images = productImageMapper.selectList(Wrappers.<PmsProductImage>lambdaQuery()
                .eq(PmsProductImage::getSpuId, id)
                .eq(PmsProductImage::getSkuId, SPU_LEVEL_IMAGE)
                .orderByAsc(PmsProductImage::getType)
                .orderByAsc(PmsProductImage::getSort));
        vo.setImages(images.stream().map(img -> {
            SpuDetailVO.ImageVO iv = new SpuDetailVO.ImageVO();
            iv.setUrl(img.getUrl());
            iv.setType(img.getType());
            iv.setSort(img.getSort());
            return iv;
        }).toList());

        // 属性：拆成规格/参数
        List<PmsSpuAttribute> spuAttrs = spuAttributeMapper.selectList(Wrappers.<PmsSpuAttribute>lambdaQuery()
                .eq(PmsSpuAttribute::getSpuId, id)
                .orderByAsc(PmsSpuAttribute::getSort)
                .orderByAsc(PmsSpuAttribute::getId));

        // SKU：一次查出全部 SKU 的规格组合（这正是 pms_sku_attribute_value 冗余 spu_id 的价值）
        List<PmsSku> skus = skuMapper.selectList(Wrappers.<PmsSku>lambdaQuery()
                .eq(PmsSku::getSpuId, id)
                .orderByAsc(PmsSku::getId));
        List<PmsSkuAttributeValue> allAv = skus.isEmpty() ? List.of()
                : skuAttributeValueMapper.selectList(Wrappers.<PmsSkuAttributeValue>lambdaQuery()
                        .eq(PmsSkuAttributeValue::getSpuId, id)
                        .orderByAsc(PmsSkuAttributeValue::getAttributeId)
                        .orderByAsc(PmsSkuAttributeValue::getAttributeValueId));
        Map<Long, List<PmsSkuAttributeValue>> avBySku = allAv.stream()
                .collect(Collectors.groupingBy(PmsSkuAttributeValue::getSkuId));
        // 按属性分组：当 pms_spu_attribute.value_json 为空时（例如历史数据），
        // 用它来推导「这个 SPU 到底选了哪些属性值」——pms_sku_attribute_value 才是权威来源
        Map<Long, List<PmsSkuAttributeValue>> avByAttr = allAv.stream()
                .collect(Collectors.groupingBy(PmsSkuAttributeValue::getAttributeId));

        vo.setSpecAttributes(buildAttrVOs(spuAttrs, ATTR_TYPE_SPEC, avByAttr));
        vo.setParamAttributes(buildAttrVOs(spuAttrs, ATTR_TYPE_PARAM, Map.of()));

        vo.setSkus(skus.stream()
                .map(sku -> toSkuVO(sku, avBySku.getOrDefault(sku.getId(), List.of())))
                .toList());
        return vo;
    }

    /* ==================== 保存 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SpuSaveDTO dto) {
        boolean isCreate = dto.getId() == null;
        assertCategoryExists(dto.getCategoryId());
        assertBrandExists(dto.getBrandId());

        // ① 规格属性解析与校验（含「属性属于该类目」「必须是 type=1」）
        SpecContext spec = resolveSpecContext(dto);
        // ② SKU 组合校验（值合法、每个规格属性恰好一个值、组合不重复）
        Map<String, SpuSaveDTO.SkuItem> comboToItem = validateSkus(dto, spec);

        // ③ SPU 主体
        PmsSpu spu = new PmsSpu();
        if (!isCreate) {
            requireSpu(dto.getId());
            spu.setId(dto.getId());
        }
        spu.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        spu.setCategoryId(dto.getCategoryId());
        spu.setBrandId(dto.getBrandId());
        spu.setSpuName(dto.getSpuName().trim());
        spu.setSubTitle(dto.getSubTitle());
        spu.setKeywords(dto.getKeywords());
        spu.setUnit(StringUtils.hasText(dto.getUnit()) ? dto.getUnit() : "件");
        spu.setWeight(dto.getWeight());
        spu.setSort(dto.getSort() == null ? 0 : dto.getSort());
        spu.setIsNew(dto.getIsNew() == null ? 0 : dto.getIsNew());
        spu.setIsRecommend(dto.getIsRecommend() == null ? 0 : dto.getIsRecommend());
        spu.setDetailHtml(dto.getDetailHtml());
        // 默认直接审核通过（本项目没有审核工作流），上下架默认下架
        spu.setPublishStatus(dto.getPublishStatus() == null ? 2 : dto.getPublishStatus());
        spu.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        if (isCreate) {
            spu.setCreateBy(SecurityUtils.getLoginUserOrNull() == null ? null : SecurityUtils.getUserId());
            // 冗余汇总字段先给默认值，稍后由 refreshAggregate 重算
            spu.setPrice(0L);
            spu.setOriginalPrice(0L);
            spu.setStock(0);
            spu.setSaleCount(0);
        }
        if (isCreate) {
            spuMapper.insert(spu);
        } else {
            spuMapper.updateById(spu);
        }
        Long spuId = spu.getId();

        // ④ 属性（先清后建：属性集合整体替换，比逐个 diff 更不容易漏）
        rebuildSpuAttributes(spuId, dto, spec);

        // ⑤ 图片（同样先清后建），并双写回 SPU 的冗余列
        rebuildImages(spuId, dto);

        // ⑥ SKU 同步
        syncSkus(spuId, dto, spec, comboToItem, isCreate);

        // ⑦ 重算 SPU 上的冗余汇总（最低价、总库存）
        refreshAggregate(spuId);

        log.info("{}商品 | spuId={} | name={} | SKU {} 个 | 规格属性 {} 个", isCreate ? "新增" : "修改",
                spuId, spu.getSpuName(), dto.getSkus().size(), spec.attributes.size());
        return spuId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ResultCode.BAD_REQUEST, "上下架状态只能是 0 或 1");
        }
        PmsSpu spu = requireSpu(id);
        if (status == 1 && !Objects.equals(spu.getPublishStatus(), 2)) {
            // 未审核通过不允许上架——否则会出现「前台能看到但根本不该卖」的商品
            throw new BizException(ResultCode.BAD_REQUEST, "商品未审核通过（当前状态：" + spu.getPublishStatus() + "），不能上架");
        }
        PmsSpu update = new PmsSpu();
        update.setId(id);
        update.setStatus(status);
        spuMapper.updateById(update);

        // SKU 的启用状态跟随 SPU：下架时把 SKU 一并禁用，
        // 避免 C 端通过 SKU 接口直接下单买到一个已下架的商品
        PmsSku skuUpdate = new PmsSku();
        skuUpdate.setStatus(status);
        skuMapper.update(skuUpdate, Wrappers.<PmsSku>lambdaUpdate().eq(PmsSku::getSpuId, id));
        log.info("商品{} | spuId={}", status == 1 ? "上架" : "下架", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PmsSpu spu = requireSpu(id);

        List<PmsSku> skus = skuMapper.selectList(Wrappers.<PmsSku>lambdaQuery().eq(PmsSku::getSpuId, id));
        for (PmsSku sku : skus) {
            // 用 softDelete 而不是 deleteById：sku_code 有唯一索引，见 docs/04 §1.7 方案 C
            skuMapper.softDelete(sku.getId());
            // 同步把库存记录也逻辑删除：SKU 已经不存在了，留着它的库存行
            // 会让「按 sku_id 聚合的库存统计」出现无主的幽灵数据。
            // 注意：库存**流水**（wms_stock_log）保留不动 —— 那是审计凭据，永远不删。
            stockMapper.delete(Wrappers.<WmsStock>lambdaQuery().eq(WmsStock::getSkuId, sku.getId()));
        }
        // 关联数据一并清理：
        // · pms_spu_attribute / pms_sku_attribute_value 唯一列都是关联 ID，用物理删除
        // · pms_product_image 只有主键索引，逻辑删除即可
        spuAttributeMapper.physicalDeleteBySpuId(id);
        skuAttributeValueMapper.physicalDeleteBySpuId(id);
        productImageMapper.delete(Wrappers.<PmsProductImage>lambdaQuery().eq(PmsProductImage::getSpuId, id));

        // pms_spu 只有主键索引，没有唯一索引冲突问题，直接逻辑删除
        spuMapper.deleteById(id);
        log.info("删除商品 | spuId={} | name={} | 连带删除 SKU {} 个", id, spu.getSpuName(), skus.size());
    }

    /* ==================== 保存：分步实现 ==================== */

    /**
     * 解析并校验规格属性，返回一个可直接使用的上下文。
     * <p>校验点：属性存在、属于该 SPU 的类目、类型必须是 1（规格属性）、选中的值属于该属性。
     */
    private SpecContext resolveSpecContext(SpuSaveDTO dto) {
        List<SpuSaveDTO.SpecAttributeItem> items =
                dto.getSpecAttributes() == null ? List.of() : dto.getSpecAttributes();
        if (items.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "至少需要声明一个规格属性，否则无法生成 SKU");
        }

        Map<Long, SpuSaveDTO.SpecAttributeItem> itemByAttr = new LinkedHashMap<>();
        for (SpuSaveDTO.SpecAttributeItem item : items) {
            if (itemByAttr.putIfAbsent(item.getAttributeId(), item) != null) {
                throw new BizException(ResultCode.BAD_REQUEST, "规格属性重复声明：attributeId=" + item.getAttributeId());
            }
            if (CollectionUtils.isEmpty(item.getValueIds())) {
                throw new BizException(ResultCode.ATTRIBUTE_VALUE_REQUIRED);
            }
            // 同一个属性下选中的值不能重复，否则该属性的笛卡尔积会算错
            if (new HashSet<>(item.getValueIds()).size() != item.getValueIds().size()) {
                throw new BizException(ResultCode.BAD_REQUEST, "同一规格属性下选中的属性值不能重复");
            }
        }

        List<PmsAttribute> attributes = attributeMapper.selectBatchIds(itemByAttr.keySet());
        if (attributes.size() != itemByAttr.size()) {
            throw new BizException(ResultCode.ATTRIBUTE_NOT_FOUND);
        }
        for (PmsAttribute attr : attributes) {
            if (!Objects.equals(attr.getCategoryId(), dto.getCategoryId())) {
                throw new BizException(ResultCode.ATTRIBUTE_CATEGORY_MISMATCH,
                        "属性「" + attr.getName() + "」不属于所选类目");
            }
            if (!Objects.equals(attr.getType(), ATTR_TYPE_SPEC)) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "属性「" + attr.getName() + "」是参数属性，不能作为规格参与 SKU 生成");
            }
        }
        // 按 sort 排序，保证 spec_json / spec_desc 的顺序稳定
        attributes.sort(Comparator.comparing((PmsAttribute a) -> a.getSort() == null ? 0 : a.getSort())
                .thenComparing(PmsAttribute::getId));

        Set<Long> allValueIds = itemByAttr.values().stream()
                .flatMap(i -> i.getValueIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, PmsAttributeValue> valueById = attributeValueMapper.selectBatchIds(allValueIds).stream()
                .collect(Collectors.toMap(PmsAttributeValue::getId, Function.identity()));
        if (valueById.size() != allValueIds.size()) {
            throw new BizException(ResultCode.BAD_REQUEST, "存在无效的属性值ID");
        }

        Map<Long, List<Long>> valueIdsByAttr = new LinkedHashMap<>();
        Map<Long, List<String>> valueNamesByAttr = new LinkedHashMap<>();
        for (PmsAttribute attr : attributes) {
            SpuSaveDTO.SpecAttributeItem item = itemByAttr.get(attr.getId());
            List<Long> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Long vid : item.getValueIds()) {
                PmsAttributeValue value = valueById.get(vid);
                if (!Objects.equals(value.getAttributeId(), attr.getId())) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "属性值「" + value.getValue() + "」不属于属性「" + attr.getName() + "」");
                }
                ids.add(vid);
                names.add(value.getValue());
            }
            valueIdsByAttr.put(attr.getId(), ids);
            valueNamesByAttr.put(attr.getId(), names);
        }

        SpecContext ctx = new SpecContext();
        ctx.attributes = attributes;
        ctx.attrById = attributes.stream().collect(Collectors.toMap(PmsAttribute::getId, Function.identity()));
        ctx.valueById = valueById;
        ctx.valueIdsByAttr = valueIdsByAttr;
        ctx.valueNamesByAttr = valueNamesByAttr;
        return ctx;
    }

    /**
     * 校验 SKU 组合并返回「组合键 → SKU 入参」的映射。
     * <p><b>不要求提交的组合等于全笛卡尔积</b>：真实业务里很多组合不存在
     * （没有「红色 + 512G」），前端需要据此置灰不可选项。
     * 但会严格校验每个组合本身合法、且互不重复。
     */
    private Map<String, SpuSaveDTO.SkuItem> validateSkus(SpuSaveDTO dto, SpecContext spec) {
        if (CollectionUtils.isEmpty(dto.getSkus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "至少要有一个 SKU");
        }
        Map<String, SpuSaveDTO.SkuItem> comboToItem = new LinkedHashMap<>();
        Set<String> skuCodes = new HashSet<>();

        for (SpuSaveDTO.SkuItem item : dto.getSkus()) {
            if (item.getPrice() == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "SKU 销售价不能为空");
            }
            List<SpuSaveDTO.SpecValueRef> refs = item.getSpecValues();
            if (CollectionUtils.isEmpty(refs) || refs.size() != spec.attributes.size()) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "每个 SKU 必须对全部 " + spec.attributes.size() + " 个规格属性各指定一个值");
            }
            Set<Long> seenAttr = new HashSet<>();
            List<Long> valueIds = new ArrayList<>(refs.size());
            for (SpuSaveDTO.SpecValueRef ref : refs) {
                if (!seenAttr.add(ref.getAttributeId())) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "SKU 对同一规格属性指定了多个值：attributeId=" + ref.getAttributeId());
                }
                List<Long> declared = spec.valueIdsByAttr.get(ref.getAttributeId());
                if (declared == null || !declared.contains(ref.getValueId())) {
                    PmsAttributeValue value = spec.valueById.get(ref.getValueId());
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "SKU 使用了未在本商品规格中声明的属性值：" + (value == null ? ref.getValueId() : value.getValue()));
                }
                valueIds.add(ref.getValueId());
            }
            String combo = comboKey(valueIds);
            if (comboToItem.putIfAbsent(combo, item) != null) {
                throw new BizException(ResultCode.BAD_REQUEST, "存在两个完全相同的规格组合，请检查 SKU 列表");
            }
            if (StringUtils.hasText(item.getSkuCode()) && !skuCodes.add(item.getSkuCode().trim())) {
                throw new BizException(ResultCode.BAD_REQUEST, "SKU 编码重复：" + item.getSkuCode());
            }
        }
        return comboToItem;
    }

    /** 规格组合键：把值ID升序拼起来。用它做「同一组合」的判定，与顺序无关。 */
    private String comboKey(List<Long> valueIds) {
        return valueIds.stream().sorted().map(String::valueOf).collect(Collectors.joining("-"));
    }

    private void rebuildSpuAttributes(Long spuId, SpuSaveDTO dto, SpecContext spec) {
        // 整体替换，用【物理删除】：
        // 唯一索引 (spu_id, attribute_id, deleted) 的两个唯一列都是关联 ID，
        // 无法用「删除时改写唯一列」绕开 key 冲突；而本表数据是纯派生的，整体重建即可。
        // 详见 PmsSpuAttributeMapper.xml 的注释。
        spuAttributeMapper.physicalDeleteBySpuId(spuId);

        int sort = 0;
        // 规格属性：value_json 存该 SPU 选中的属性值名称数组（编辑表单直接可读）
        for (PmsAttribute attr : spec.attributes) {
            PmsSpuAttribute entity = new PmsSpuAttribute();
            entity.setSpuId(spuId);
            entity.setAttributeId(attr.getId());
            entity.setAttributeName(attr.getName());
            entity.setType(ATTR_TYPE_SPEC);
            entity.setValueJson(toJson(spec.valueNamesByAttr.get(attr.getId())));
            entity.setSort(sort);
            spuAttributeMapper.insert(entity);
            sort += 10;
        }
        // 参数属性：只存取值，不参与 SKU
        List<SpuSaveDTO.ParamAttributeItem> params =
                dto.getParamAttributes() == null ? List.of() : dto.getParamAttributes();
        for (SpuSaveDTO.ParamAttributeItem item : params) {
            PmsAttribute attr = attributeMapper.selectById(item.getAttributeId());
            if (attr == null) {
                throw new BizException(ResultCode.ATTRIBUTE_NOT_FOUND);
            }
            if (!Objects.equals(attr.getType(), ATTR_TYPE_PARAM)) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "属性「" + attr.getName() + "」不是参数属性，应放在规格属性里");
            }
            PmsSpuAttribute entity = new PmsSpuAttribute();
            entity.setSpuId(spuId);
            entity.setAttributeId(attr.getId());
            entity.setAttributeName(attr.getName());
            entity.setType(ATTR_TYPE_PARAM);
            entity.setValueJson(toJson(item.getValues() == null ? List.of() : item.getValues()));
            entity.setSort(item.getSort() == null ? sort : item.getSort());
            spuAttributeMapper.insert(entity);
            sort += 10;
        }
    }

    /**
     * 重建图片，并把主图/轮播图汇总写回 {@code pms_spu} 的两个冗余列。
     * <p>这就是「一份数据两种表示」的双写：本表用于管理，SPU 上的列用于列表页零 JOIN 读取。
     */
    private void rebuildImages(Long spuId, SpuSaveDTO dto) {
        productImageMapper.delete(Wrappers.<PmsProductImage>lambdaQuery()
                .eq(PmsProductImage::getSpuId, spuId)
                .eq(PmsProductImage::getSkuId, SPU_LEVEL_IMAGE));

        List<SpuSaveDTO.ImageItem> images = dto.getImages() == null ? List.of() : dto.getImages();
        String mainImage = null;
        List<String> album = new ArrayList<>();
        int sort = 0;
        for (SpuSaveDTO.ImageItem item : images) {
            PmsProductImage entity = new PmsProductImage();
            entity.setSpuId(spuId);
            entity.setSkuId(SPU_LEVEL_IMAGE);
            entity.setUrl(item.getUrl());
            entity.setType(item.getType());
            entity.setSort(item.getSort() == null ? sort : item.getSort());
            productImageMapper.insert(entity);

            if (Objects.equals(item.getType(), IMAGE_MAIN) && mainImage == null) {
                mainImage = item.getUrl();
            }
            if (Objects.equals(item.getType(), IMAGE_ALBUM)) {
                album.add(item.getUrl());
            }
            sort += 10;
        }

        PmsSpu update = new PmsSpu();
        update.setId(spuId);
        update.setMainImage(mainImage);
        update.setAlbumImages(toJson(album));
        spuMapper.updateById(update);
    }

    /**
     * SKU 增量同步 —— <b>本项目最需要小心的一段逻辑</b>。
     * <p>
     * 为什么不能「全删重建」：SKU 的 {@code id} 会被购物车、订单明细引用。
     * 一旦删除重建，购物车里的商品就会指向不存在的 SKU，而且已有价格/销量全部丢失。
     * <p>
     * 匹配策略（按优先级）：
     * <ol>
     *   <li>入参带了 {@code id} → 按 id 匹配（前端编辑已有 SKU 的正常路径）；</li>
     *   <li>没带 {@code id} → <b>按规格组合匹配</b>。这样即使前端提交时丢了 id
     *       （比如整体替换式提交），也能保住已有 SKU 的价格与销量；</li>
     *   <li>都匹配不上 → 视为新增；</li>
     *   <li>库里存在但入参里没有的组合 → 逻辑删除。</li>
     * </ol>
     */
    private void syncSkus(Long spuId, SpuSaveDTO dto, SpecContext spec,
                          Map<String, SpuSaveDTO.SkuItem> comboToItem, boolean isCreate) {
        List<PmsSku> existingSkus = skuMapper.selectList(Wrappers.<PmsSku>lambdaQuery().eq(PmsSku::getSpuId, spuId));
        Map<Long, PmsSku> existingById = existingSkus.stream()
                .collect(Collectors.toMap(PmsSku::getId, Function.identity()));

        // 组合 -> 已有 SKU ID。pms_sku_attribute_value 冗余了 spu_id，正好一条 SQL 取全
        Map<Long, List<PmsSkuAttributeValue>> avBySku = existingSkus.isEmpty() ? Map.of()
                : skuAttributeValueMapper.selectList(Wrappers.<PmsSkuAttributeValue>lambdaQuery()
                        .eq(PmsSkuAttributeValue::getSpuId, spuId))
                .stream().collect(Collectors.groupingBy(PmsSkuAttributeValue::getSkuId));
        Map<String, Long> comboToExistingSkuId = new HashMap<>();
        avBySku.forEach((skuId, avs) -> {
            if (!avs.isEmpty()) {
                comboToExistingSkuId.put(comboKey(avs.stream()
                        .map(PmsSkuAttributeValue::getAttributeValueId).toList()), skuId);
            }
        });

        Set<Long> keptSkuIds = new HashSet<>();
        for (SpuSaveDTO.SkuItem item : dto.getSkus()) {
            String combo = comboKey(item.getSpecValues().stream().map(SpuSaveDTO.SpecValueRef::getValueId).toList());

            PmsSku target = null;
            if (item.getId() != null) {
                target = existingById.get(item.getId());
                if (target == null) {
                    throw new BizException(ResultCode.BAD_REQUEST, "SKU(id=" + item.getId() + ")不属于该商品");
                }
            } else {
                Long matchedId = comboToExistingSkuId.get(combo);
                if (matchedId != null) {
                    target = existingById.get(matchedId);
                    log.debug("SKU 未带 id，按规格组合匹配到已有 SKU | spuId={} | combo={} | skuId={}", spuId, combo, matchedId);
                }
            }

            // 规格明细（顺序按属性 sort，保证 spec_json 与 spec_desc 稳定）
            List<PmsSkuAttributeValue> avs = new ArrayList<>();
            List<String> valueNames = new ArrayList<>();
            for (PmsAttribute attr : spec.attributes) {
                SpuSaveDTO.SpecValueRef ref = item.getSpecValues().stream()
                        .filter(r -> Objects.equals(r.getAttributeId(), attr.getId()))
                        .findFirst()
                        .orElseThrow(() -> new BizException(ResultCode.BAD_REQUEST, "SKU 缺少规格属性：" + attr.getName()));
                PmsAttributeValue value = spec.valueById.get(ref.getValueId());
                PmsSkuAttributeValue av = new PmsSkuAttributeValue();
                av.setSkuId(null); // 稍后填
                av.setSpuId(spuId);
                av.setAttributeId(attr.getId());
                av.setAttributeName(attr.getName());
                av.setAttributeValueId(value.getId());
                av.setAttributeValue(value.getValue());
                av.setSort(attr.getSort());
                avs.add(av);
                valueNames.add(value.getValue());
            }
            String specDesc = String.join(" / ", valueNames);

            PmsSku sku = new PmsSku();
            boolean isNewSku = target == null;
            if (!isNewSku) {
                sku.setId(target.getId());
            }
            sku.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
            sku.setSpuId(spuId);
            sku.setSkuCode(StringUtils.hasText(item.getSkuCode())
                    ? item.getSkuCode().trim()
                    // 自动编码：SPU{spuId}-{值ID组合}。同一 SPU 内组合唯一，所以不会撞码
                    : "SPU" + spuId + "-" + combo);
            sku.setSkuName(dto.getSpuName().trim() + " " + specDesc);
            sku.setSpecDesc(specDesc);
            sku.setSpecJson(toJson(avs.stream().map(av -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("attributeId", av.getAttributeId());
                m.put("attributeName", av.getAttributeName());
                m.put("valueId", av.getAttributeValueId());
                m.put("valueName", av.getAttributeValue());
                return m;
            }).toList()));
            sku.setImage(item.getImage());
            sku.setPrice(item.getPrice());
            sku.setOriginalPrice(item.getOriginalPrice() == null ? 0L : item.getOriginalPrice());
            sku.setCostPrice(item.getCostPrice() == null ? 0L : item.getCostPrice());
            sku.setStock(item.getStock() == null ? 0 : item.getStock());
            sku.setLockedStock(isNewSku ? 0 : (target.getLockedStock() == null ? 0 : target.getLockedStock()));
            sku.setSaleCount(isNewSku ? 0 : (target.getSaleCount() == null ? 0 : target.getSaleCount()));
            sku.setWeight(item.getWeight() == null ? dto.getWeight() : item.getWeight());
            sku.setStatus(item.getStatus() == null ? 1 : item.getStatus());

            if (isNewSku) {
                skuMapper.insert(sku);
            } else {
                skuMapper.updateById(sku);
            }
            keptSkuIds.add(sku.getId());

            // 重建该 SKU 的规格组合明细（整体替换，物理删除——理由见 PmsSkuAttributeValueMapper.xml）
            skuAttributeValueMapper.physicalDeleteBySkuId(sku.getId());
            for (PmsSkuAttributeValue av : avs) {
                av.setSkuId(sku.getId());
                skuAttributeValueMapper.insert(av);
            }

            // 权威库存：新建走 init，已有走 adjust（会自动写流水）
            int stock = item.getStock() == null ? 0 : item.getStock();
            if (isNewSku) {
                stockService.initStock(sku.getId(), stock, "商品创建初始化库存");
            } else {
                stockService.adjustStock(sku.getId(), stock, "商品编辑调整库存");
            }
        }

        // 入参里没有的组合：逻辑删除对应 SKU
        for (PmsSku old : existingSkus) {
            if (!keptSkuIds.contains(old.getId())) {
                skuMapper.softDelete(old.getId());
                skuAttributeValueMapper.physicalDeleteBySkuId(old.getId());
                log.info("删除已移除规格的 SKU | spuId={} | skuId={} | specDesc={}",
                        spuId, old.getId(), old.getSpecDesc());
            }
        }
    }

    /**
     * 重算 SPU 上的冗余汇总字段。
     * <p>这些字段只服务列表页展示，<b>绝不参与交易判断</b>（能不能卖只看 {@code wms_stock}）。
     * 每次保存后统一重算，避免增量维护漏掉某个分支导致价格区间不对。
     */
    private void refreshAggregate(Long spuId) {
        List<PmsSku> skus = skuMapper.selectList(Wrappers.<PmsSku>lambdaQuery().eq(PmsSku::getSpuId, spuId));
        long minPrice = 0L;
        long minOriginal = 0L;
        int totalStock = 0;
        boolean first = true;
        for (PmsSku sku : skus) {
            if (sku.getPrice() != null && sku.getPrice() > 0) {
                if (first || sku.getPrice() < minPrice) {
                    minPrice = sku.getPrice();
                    minOriginal = sku.getOriginalPrice() == null ? 0L : sku.getOriginalPrice();
                    first = false;
                }
            }
            totalStock += sku.getStock() == null ? 0 : sku.getStock();
        }
        PmsSpu update = new PmsSpu();
        update.setId(spuId);
        update.setPrice(minPrice);
        update.setOriginalPrice(minOriginal);
        update.setStock(totalStock);
        spuMapper.updateById(update);
    }

    /* ==================== 辅助 ==================== */

    private PmsSpu requireSpu(Long id) {
        PmsSpu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return spu;
    }

    private void assertCategoryExists(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND);
        }
    }

    private void assertBrandExists(Long brandId) {
        if (brandId != null && brandMapper.selectById(brandId) == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }
    }

    /** 把类目 ID 展开为「它自己 + 全部子孙」，用于按父类目筛选商品 */
    private List<Long> expandCategoryIds(Long rootId) {
        List<PmsCategory> all = categoryMapper.selectList(Wrappers.<PmsCategory>lambdaQuery());
        Map<Long, List<Long>> childrenOf = new HashMap<>();
        for (PmsCategory c : all) {
            childrenOf.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
        }
        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        List<Long> queue = new ArrayList<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.remove(0);
            if (!visited.add(current)) {
                continue;
            }
            result.add(current);
            queue.addAll(childrenOf.getOrDefault(current, List.of()));
        }
        return result;
    }

    private Map<Long, String> loadCategoryNames(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(PmsCategory::getId, PmsCategory::getName));
    }

    private Map<Long, String> loadBrandNames(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return brandMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(PmsBrand::getId, b -> b.getNameCn() == null ? b.getName() : b.getNameCn()));
    }

    /** 一次查出所有 SPU 的 SKU 数量，避免逐行 count 造成 N+1 */
    private Map<Long, Long> loadSkuCounts(List<Long> spuIds) {
        if (spuIds.isEmpty()) {
            return Map.of();
        }
        return skuMapper.selectList(Wrappers.<PmsSku>lambdaQuery()
                        .select(PmsSku::getId, PmsSku::getSpuId)
                        .in(PmsSku::getSpuId, spuIds))
                .stream()
                .collect(Collectors.groupingBy(PmsSku::getSpuId, Collectors.counting()));
    }

    private List<SpuDetailVO.AttrVO> buildAttrVOs(List<PmsSpuAttribute> attrs, int type,
                                                  Map<Long, List<PmsSkuAttributeValue>> avByAttr) {
        List<PmsSpuAttribute> filtered = attrs.stream()
                .filter(a -> Objects.equals(a.getType(), type))
                .toList();
        if (filtered.isEmpty()) {
            return List.of();
        }
        // 规格属性需要把「值名称」还原成「值ID」，用于编辑表单回填选中态。
        // 用一次批量查询完成，避免逐个属性查一次。
        Map<Long, Map<String, Long>> nameToIdByAttr = new HashMap<>();
        if (type == ATTR_TYPE_SPEC) {
            List<Long> attrIds = filtered.stream().map(PmsSpuAttribute::getAttributeId).toList();
            Map<Long, List<PmsAttributeValue>> byAttr = attributeValueMapper
                    .selectList(Wrappers.<PmsAttributeValue>lambdaQuery().in(PmsAttributeValue::getAttributeId, attrIds))
                    .stream().collect(Collectors.groupingBy(PmsAttributeValue::getAttributeId));
            byAttr.forEach((attrId, values) -> {
                Map<String, Long> m = new HashMap<>();
                values.forEach(v -> m.putIfAbsent(v.getValue(), v.getId()));
                nameToIdByAttr.put(attrId, m);
            });
        }

        List<SpuDetailVO.AttrVO> result = new ArrayList<>(filtered.size());
        for (PmsSpuAttribute attr : filtered) {
            SpuDetailVO.AttrVO vo = new SpuDetailVO.AttrVO();
            vo.setAttributeId(attr.getAttributeId());
            vo.setAttributeName(attr.getAttributeName());
            vo.setType(attr.getType());
            vo.setSort(attr.getSort());

            List<String> values = parseStringArray(attr.getValueJson());
            List<Long> valueIds = new ArrayList<>();
            if (type == ATTR_TYPE_SPEC) {
                if (!values.isEmpty()) {
                    // 正常路径：value_json 保留了运营选择时的顺序，按名字映射回 ID
                    Map<String, Long> nameToId = nameToIdByAttr.getOrDefault(attr.getAttributeId(), Map.of());
                    for (String name : values) {
                        Long vid = nameToId.get(name);
                        if (vid != null) {
                            valueIds.add(vid);
                        }
                    }
                } else {
                    // 回退路径：value_json 为空（历史数据 / 种子数据），
                    // 从 SKU 的规格组合里推导——pms_sku_attribute_value 才是权威来源。
                    // 这一步保证「详情接口不依赖某张表的某个字段一定有值」。
                    Map<Long, String> idToName = new LinkedHashMap<>();
                    for (PmsSkuAttributeValue av : avByAttr.getOrDefault(attr.getAttributeId(), List.of())) {
                        idToName.putIfAbsent(av.getAttributeValueId(), av.getAttributeValue());
                    }
                    valueIds.addAll(idToName.keySet());
                    values = new ArrayList<>(idToName.values());
                }
            }
            vo.setValues(values);
            vo.setValueIds(valueIds);
            result.add(vo);
        }
        return result;
    }

    private static SkuVO toSkuVO(PmsSku sku, List<PmsSkuAttributeValue> avs) {
        SkuVO vo = new SkuVO();
        vo.setId(sku.getId());
        vo.setSkuCode(sku.getSkuCode());
        vo.setSkuName(sku.getSkuName());
        vo.setSpecDesc(sku.getSpecDesc());
        vo.setImage(sku.getImage());
        vo.setPrice(sku.getPrice());
        vo.setOriginalPrice(sku.getOriginalPrice());
        vo.setCostPrice(sku.getCostPrice());
        vo.setStock(sku.getStock());
        vo.setLockedStock(sku.getLockedStock());
        vo.setSaleCount(sku.getSaleCount());
        vo.setWeight(sku.getWeight());
        vo.setStatus(sku.getStatus());
        vo.setSpecValues(avs.stream().map(av -> {
            SkuVO.SkuSpecVO sv = new SkuVO.SkuSpecVO();
            sv.setAttributeId(av.getAttributeId());
            sv.setAttributeName(av.getAttributeName());
            sv.setValueId(av.getAttributeValueId());
            sv.setValueName(av.getAttributeValue());
            return sv;
        }).toList());
        return vo;
    }

    private static SpuVO toVO(PmsSpu spu, Map<Long, String> categoryNames,
                              Map<Long, String> brandNames, Map<Long, Long> skuCounts) {
        SpuVO vo = new SpuVO();
        fillBase(vo, spu, categoryNames, brandNames, skuCounts);
        return vo;
    }

    private static void fillBase(SpuVO vo, PmsSpu spu, Map<Long, String> categoryNames,
                                 Map<Long, String> brandNames, Map<Long, Long> skuCounts) {
        vo.setId(spu.getId());
        vo.setSpuName(spu.getSpuName());
        vo.setSubTitle(spu.getSubTitle());
        vo.setCategoryId(spu.getCategoryId());
        vo.setCategoryName(categoryNames.get(spu.getCategoryId()));
        vo.setBrandId(spu.getBrandId());
        vo.setBrandName(spu.getBrandId() == null ? null : brandNames.get(spu.getBrandId()));
        vo.setMainImage(spu.getMainImage());
        vo.setPrice(spu.getPrice());
        vo.setOriginalPrice(spu.getOriginalPrice());
        vo.setStock(spu.getStock());
        vo.setSaleCount(spu.getSaleCount());
        vo.setSkuCount(skuCounts.getOrDefault(spu.getId(), 0L).intValue());
        vo.setPublishStatus(spu.getPublishStatus());
        vo.setStatus(spu.getStatus());
        vo.setIsNew(spu.getIsNew());
        vo.setIsRecommend(spu.getIsRecommend());
        vo.setSort(spu.getSort());
        vo.setCreateTime(spu.getCreateTime());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "JSON 序列化失败");
        }
    }

    private List<String> parseStringArray(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.warn("JSON 解析失败，按空数组处理 | json={}", json);
            return new ArrayList<>();
        }
    }

    /** 规格属性解析结果，避免在多个方法间反复传一堆 Map */
    private static class SpecContext {
        List<PmsAttribute> attributes;
        Map<Long, PmsAttribute> attrById;
        Map<Long, PmsAttributeValue> valueById;
        Map<Long, List<Long>> valueIdsByAttr;
        Map<Long, List<String>> valueNamesByAttr;
    }
}
