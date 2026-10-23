package com.shopping.service.product.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.PageResult;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.PmsAttributeMapper;
import com.shopping.dao.mapper.PmsAttributeValueMapper;
import com.shopping.dao.mapper.PmsCategoryMapper;
import com.shopping.dao.mapper.PmsSkuAttributeValueMapper;
import com.shopping.dao.mapper.PmsSpuAttributeMapper;
import com.shopping.model.dto.AttributeDTO;
import com.shopping.model.entity.PmsAttribute;
import com.shopping.model.entity.PmsAttributeValue;
import com.shopping.model.entity.PmsCategory;
import com.shopping.model.entity.PmsSkuAttributeValue;
import com.shopping.model.entity.PmsSpuAttribute;
import com.shopping.model.query.AttributeQuery;
import com.shopping.model.vo.AttributeVO;
import com.shopping.model.vo.AttributeValueVO;
import com.shopping.service.product.AttributeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 属性服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttributeServiceImpl implements AttributeService {

    /** 规格属性：参与 SKU 生成 */
    private static final int TYPE_SPEC = 1;
    /** 参数属性：仅展示，不参与 SKU */
    private static final int TYPE_PARAM = 2;

    private final PmsAttributeMapper attributeMapper;
    private final PmsAttributeValueMapper attributeValueMapper;
    private final PmsCategoryMapper categoryMapper;
    private final PmsSpuAttributeMapper spuAttributeMapper;
    private final PmsSkuAttributeValueMapper skuAttributeValueMapper;

    @Override
    public PageResult<AttributeVO> page(AttributeQuery query) {
        Page<PmsAttribute> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<PmsAttribute> wrapper = Wrappers.<PmsAttribute>lambdaQuery()
                .eq(query.getCategoryId() != null, PmsAttribute::getCategoryId, query.getCategoryId())
                .eq(query.getType() != null, PmsAttribute::getType, query.getType())
                .like(StringUtils.hasText(query.getName()), PmsAttribute::getName, query.getName())
                .eq(query.getStatus() != null, PmsAttribute::getStatus, query.getStatus())
                .orderByAsc(PmsAttribute::getSort)
                .orderByAsc(PmsAttribute::getId);

        Page<PmsAttribute> result = attributeMapper.selectPage(page, wrapper);

        // 一次性把本页所有属性的值查出来再分组，避免「每条属性查一次值」的 N+1。
        // 一页 10 条属性时，N+1 是 11 次查询，这里是 2 次。
        Map<Long, List<PmsAttributeValue>> valueMap = loadValuesGrouped(
                result.getRecords().stream().map(PmsAttribute::getId).toList());

        List<AttributeVO> records = result.getRecords().stream()
                .map(a -> toVO(a, valueMap.getOrDefault(a.getId(), List.of())))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Override
    public AttributeVO detail(Long id) {
        PmsAttribute attribute = attributeMapper.selectById(id);
        if (attribute == null) {
            throw new BizException(ResultCode.ATTRIBUTE_NOT_FOUND);
        }
        return toVO(attribute, listValues(id));
    }

    @Override
    public List<AttributeVO> listSpecByCategory(Long categoryId) {
        List<PmsAttribute> attributes = attributeMapper.selectList(
                Wrappers.<PmsAttribute>lambdaQuery()
                        .eq(PmsAttribute::getCategoryId, categoryId)
                        .eq(PmsAttribute::getType, TYPE_SPEC)
                        .eq(PmsAttribute::getStatus, 1)
                        .orderByAsc(PmsAttribute::getSort)
                        .orderByAsc(PmsAttribute::getId));
        if (attributes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PmsAttributeValue>> valueMap = loadValuesGrouped(
                attributes.stream().map(PmsAttribute::getId).toList());
        return attributes.stream()
                .map(a -> toVO(a, valueMap.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AttributeDTO dto) {
        assertCategoryExists(dto.getCategoryId());
        assertNameUnique(dto.getCategoryId(), dto.getName(), null);
        List<AttributeDTO.AttributeValueItem> values = normalizeValues(dto);

        PmsAttribute entity = new PmsAttribute();
        entity.setCategoryId(dto.getCategoryId());
        applyDto(entity, dto);
        attributeMapper.insert(entity);

        insertValues(entity.getId(), values);
        log.info("新增属性 | id={} | name={} | type={} | 属性值 {} 个",
                entity.getId(), entity.getName(), entity.getType(), values.size());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AttributeDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "修改属性时必须传 id");
        }
        PmsAttribute existing = attributeMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BizException(ResultCode.ATTRIBUTE_NOT_FOUND);
        }
        assertCategoryExists(dto.getCategoryId());
        assertNameUnique(dto.getCategoryId(), dto.getName(), dto.getId());
        // 属性一旦被商品使用，就不允许改类型：从参数改成规格会让已生成的 SKU 语义失效
        if (!Objects.equals(existing.getType(), dto.getType())) {
            assertAttributeNotUsed(dto.getId(), "修改属性类型");
        }
        List<AttributeDTO.AttributeValueItem> values = normalizeValues(dto);

        PmsAttribute update = new PmsAttribute();
        update.setId(dto.getId());
        applyDto(update, dto);
        attributeMapper.updateById(update);

        syncValues(dto.getId(), values);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PmsAttribute existing = attributeMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ResultCode.ATTRIBUTE_NOT_FOUND);
        }
        assertAttributeNotUsed(id, "删除属性");

        // 属性值随属性一起删（逻辑删除）。
        // 用 softDelete 而不是 deleteById：后者只 SET deleted=1，
        // 若同一属性下已存在同名已删值，会撞唯一索引 uk_attribute_id_value。
        List<PmsAttributeValue> values = listValues(id);
        for (PmsAttributeValue value : values) {
            attributeValueMapper.softDelete(value.getId());
        }
        attributeMapper.softDelete(id);
        log.info("删除属性 | id={} | name={} | 连带删除属性值 {} 个", id, existing.getName(), values.size());
    }

    /* ---------------- 内部方法 ---------------- */

    private void assertCategoryExists(Long categoryId) {
        Long count = categoryMapper.selectCount(
                Wrappers.<PmsCategory>lambdaQuery().eq(PmsCategory::getId, categoryId));
        if (count == null || count == 0) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND, "所选类目不存在");
        }
    }

    private void assertNameUnique(Long categoryId, String name, Long excludeId) {
        Long count = attributeMapper.selectCount(Wrappers.<PmsAttribute>lambdaQuery()
                .eq(PmsAttribute::getCategoryId, categoryId)
                .eq(PmsAttribute::getName, name.trim())
                .ne(excludeId != null, PmsAttribute::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "该类目下已存在名为「" + name.trim() + "」的属性");
        }
    }

    /**
     * 校验并规整属性值。
     * <p><b>核心规则：规格属性必须至少有一个属性值。</b>
     * 因为 SKU 是由规格值笛卡尔积生成的——没有值就没有 SKU，商品将无法售卖。
     * 这类「字段之间的条件依赖」注解表达不了（{@code @NotEmpty} 做不到「当 type=1 时才必填」），
     * 只能写在 Service 里。
     */
    private List<AttributeDTO.AttributeValueItem> normalizeValues(AttributeDTO dto) {
        List<AttributeDTO.AttributeValueItem> raw = dto.getValues() == null ? List.of() : dto.getValues();
        List<AttributeDTO.AttributeValueItem> cleaned = raw.stream()
                .filter(v -> v != null && StringUtils.hasText(v.getValue()))
                .toList();

        if (Objects.equals(dto.getType(), TYPE_SPEC) && cleaned.isEmpty()) {
            throw new BizException(ResultCode.ATTRIBUTE_VALUE_REQUIRED);
        }
        // 手工输入型（inputType=3）的参数属性允许没有预定义值
        if (Objects.equals(dto.getType(), TYPE_PARAM)
                && Objects.equals(dto.getInputType(), 3)
                && cleaned.isEmpty()) {
            return List.of();
        }

        Set<String> seen = new HashSet<>();
        for (AttributeDTO.AttributeValueItem item : cleaned) {
            String v = item.getValue().trim();
            if (!seen.add(v)) {
                throw new BizException(ResultCode.BAD_REQUEST, "属性值「" + v + "」重复");
            }
            item.setValue(v);
        }
        return cleaned;
    }

    private void insertValues(Long attributeId, List<AttributeDTO.AttributeValueItem> values) {
        int sort = 0;
        for (AttributeDTO.AttributeValueItem item : values) {
            PmsAttributeValue entity = new PmsAttributeValue();
            entity.setAttributeId(attributeId);
            entity.setValue(item.getValue());
            entity.setSort(item.getSort() == null ? sort : item.getSort());
            entity.setStatus(item.getStatus() == null ? 1 : item.getStatus());
            attributeValueMapper.insert(entity);
            sort += 10;
        }
    }

    /**
     * 增量同步属性值：请求里有的就更新/新增，DB 里有但请求里没有的就删除。
     * <p><b>删除前必须检查是否已被 SKU 使用</b>：{@code pms_sku_attribute_value.attribute_value_id}
     * 指向它，删掉会让已生成的 SKU 规格组合变成指向不存在的值。
     * 数据库没有物理外键，这个保护只能由应用层提供——这就是「不建外键」的代价。
     */
    private void syncValues(Long attributeId, List<AttributeDTO.AttributeValueItem> values) {
        List<PmsAttributeValue> existing = listValues(attributeId);
        Set<Long> incomingIds = values.stream()
                .map(AttributeDTO.AttributeValueItem::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (PmsAttributeValue old : existing) {
            if (incomingIds.contains(old.getId())) {
                continue;
            }
            Long used = skuAttributeValueMapper.selectCount(
                    Wrappers.<PmsSkuAttributeValue>lambdaQuery()
                            .eq(PmsSkuAttributeValue::getAttributeValueId, old.getId()));
            if (used != null && used > 0) {
                throw new BizException(ResultCode.ATTRIBUTE_VALUE_IN_USE,
                        "属性值「" + old.getValue() + "」已被 " + used + " 个 SKU 使用，不能删除，请改为禁用");
            }
            attributeValueMapper.softDelete(old.getId());
        }

        int sort = 0;
        for (AttributeDTO.AttributeValueItem item : values) {
            PmsAttributeValue entity = new PmsAttributeValue();
            entity.setId(item.getId());
            entity.setAttributeId(attributeId);
            entity.setValue(item.getValue());
            entity.setSort(item.getSort() == null ? sort : item.getSort());
            entity.setStatus(item.getStatus() == null ? 1 : item.getStatus());
            if (item.getId() == null) {
                attributeValueMapper.insert(entity);
            } else {
                attributeValueMapper.updateById(entity);
            }
            sort += 10;
        }
    }

    /** 属性被 SPU 或 SKU 引用时不允许删除/改类型 */
    private void assertAttributeNotUsed(Long attributeId, String action) {
        Long spuUsed = spuAttributeMapper.selectCount(
                Wrappers.<PmsSpuAttribute>lambdaQuery().eq(PmsSpuAttribute::getAttributeId, attributeId));
        Long skuUsed = skuAttributeValueMapper.selectCount(
                Wrappers.<PmsSkuAttributeValue>lambdaQuery().eq(PmsSkuAttributeValue::getAttributeId, attributeId));
        long total = (spuUsed == null ? 0 : spuUsed) + (skuUsed == null ? 0 : skuUsed);
        if (total > 0) {
            throw new BizException(ResultCode.ATTRIBUTE_IN_USE,
                    "该属性已被商品使用（" + total + " 处引用），不能" + action);
        }
    }

    private List<PmsAttributeValue> listValues(Long attributeId) {
        return attributeValueMapper.selectList(Wrappers.<PmsAttributeValue>lambdaQuery()
                .eq(PmsAttributeValue::getAttributeId, attributeId)
                .orderByAsc(PmsAttributeValue::getSort)
                .orderByAsc(PmsAttributeValue::getId));
    }

    private Map<Long, List<PmsAttributeValue>> loadValuesGrouped(List<Long> attributeIds) {
        if (attributeIds == null || attributeIds.isEmpty()) {
            return Map.of();
        }
        return attributeValueMapper.selectList(Wrappers.<PmsAttributeValue>lambdaQuery()
                        .in(PmsAttributeValue::getAttributeId, attributeIds)
                        .orderByAsc(PmsAttributeValue::getSort)
                        .orderByAsc(PmsAttributeValue::getId))
                .stream()
                .collect(Collectors.groupingBy(PmsAttributeValue::getAttributeId));
    }

    private void applyDto(PmsAttribute entity, AttributeDTO dto) {
        entity.setCategoryId(dto.getCategoryId());
        entity.setName(dto.getName().trim());
        entity.setType(dto.getType());
        entity.setInputType(dto.getInputType() == null ? 1 : dto.getInputType());
        entity.setUnit(dto.getUnit());
        entity.setIsRequired(dto.getIsRequired() == null ? 0 : dto.getIsRequired());
        entity.setSearchable(dto.getSearchable() == null ? 0 : dto.getSearchable());
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    private static AttributeVO toVO(PmsAttribute a, List<PmsAttributeValue> values) {
        AttributeVO vo = new AttributeVO();
        vo.setId(a.getId());
        vo.setCategoryId(a.getCategoryId());
        vo.setName(a.getName());
        vo.setType(a.getType());
        vo.setInputType(a.getInputType());
        vo.setUnit(a.getUnit());
        vo.setIsRequired(a.getIsRequired());
        vo.setSearchable(a.getSearchable());
        vo.setSort(a.getSort());
        vo.setStatus(a.getStatus());

        List<AttributeValueVO> valueVOs = new ArrayList<>(values.size());
        for (PmsAttributeValue v : values) {
            AttributeValueVO vv = new AttributeValueVO();
            vv.setId(v.getId());
            vv.setAttributeId(v.getAttributeId());
            vv.setValue(v.getValue());
            vv.setSort(v.getSort());
            vv.setStatus(v.getStatus());
            valueVOs.add(vv);
        }
        vo.setValues(valueVOs);
        return vo;
    }
}
