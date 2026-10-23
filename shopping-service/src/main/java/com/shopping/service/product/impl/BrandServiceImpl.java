package com.shopping.service.product.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.PageResult;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.PmsBrandMapper;
import com.shopping.dao.mapper.PmsSpuMapper;
import com.shopping.model.dto.BrandDTO;
import com.shopping.model.entity.PmsBrand;
import com.shopping.model.entity.PmsSpu;
import com.shopping.model.query.BrandQuery;
import com.shopping.model.vo.BrandVO;
import com.shopping.service.product.BrandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final PmsBrandMapper brandMapper;
    private final PmsSpuMapper spuMapper;

    @Override
    public PageResult<BrandVO> page(BrandQuery query) {
        Page<PmsBrand> page = new Page<>(query.getCurrent(), query.getSize());

        LambdaQueryWrapper<PmsBrand> wrapper = Wrappers.<PmsBrand>lambdaQuery();
        if (StringUtils.hasText(query.getName())) {
            String keyword = query.getName().trim();
            // 一个关键词同时匹配中英文名，用 and(...) 把 or 括起来。
            // ⚠️ 如果不加 and(...) 包一层，or 会和后面的 status 条件平级，
            //    变成「(name like ? or nameCn like ? or status = ?)」——
            //    这是 QueryWrapper 最经典的踩坑点。
            wrapper.and(w -> w.like(PmsBrand::getName, keyword)
                    .or().like(PmsBrand::getNameCn, keyword));
        }
        wrapper.eq(StringUtils.hasText(query.getFirstLetter()), PmsBrand::getFirstLetter, query.getFirstLetter())
                .eq(query.getStatus() != null, PmsBrand::getStatus, query.getStatus())
                .orderByAsc(PmsBrand::getSort)
                .orderByAsc(PmsBrand::getId);

        Page<PmsBrand> result = brandMapper.selectPage(page, wrapper);
        List<BrandVO> records = result.getRecords().stream().map(BrandServiceImpl::toVO).toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Override
    public List<BrandVO> listEnabled() {
        return brandMapper.selectList(Wrappers.<PmsBrand>lambdaQuery()
                        .eq(PmsBrand::getStatus, 1)
                        .orderByAsc(PmsBrand::getSort)
                        .orderByAsc(PmsBrand::getId))
                .stream().map(BrandServiceImpl::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(BrandDTO dto) {
        assertNameUnique(dto.getName(), null);
        PmsBrand entity = new PmsBrand();
        applyDto(entity, dto);
        brandMapper.insert(entity);
        log.info("新增品牌 | id={} | name={}", entity.getId(), entity.getName());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(BrandDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "修改品牌时必须传 id");
        }
        PmsBrand existing = brandMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }
        assertNameUnique(dto.getName(), dto.getId());
        PmsBrand update = new PmsBrand();
        update.setId(dto.getId());
        applyDto(update, dto);
        brandMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PmsBrand existing = brandMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }
        // 品牌被商品引用时不能删。数据库没有物理外键，所以这个保护必须由应用层承担——
        // 这正是「不建物理外键」的代价，也是为什么每个删除操作都要显式写守卫。
        Long spuCount = spuMapper.selectCount(
                Wrappers.<PmsSpu>lambdaQuery().eq(PmsSpu::getBrandId, id));
        if (spuCount != null && spuCount > 0) {
            throw new BizException(ResultCode.BRAND_IN_USE,
                    "该品牌下还有 " + spuCount + " 个商品，不能删除");
        }
        // 用 softDelete 而不是 deleteById：后者只会 SET deleted=1，
        // 在「同名品牌已被删过一次」时会撞唯一索引 uk_name(name, deleted)。
        // softDelete 会同时改写 name，见 docs/04 §1.7 方案 C。
        brandMapper.softDelete(id);
        log.info("删除品牌 | id={} | name={}", id, existing.getName());
    }

    private void assertNameUnique(String name, Long excludeId) {
        Long count = brandMapper.selectCount(Wrappers.<PmsBrand>lambdaQuery()
                .eq(PmsBrand::getName, name.trim())
                .ne(excludeId != null, PmsBrand::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.BRAND_NAME_DUPLICATE, "品牌「" + name.trim() + "」已存在");
        }
    }

    private void applyDto(PmsBrand entity, BrandDTO dto) {
        entity.setName(dto.getName().trim());
        entity.setNameCn(dto.getNameCn());
        entity.setLogo(dto.getLogo());
        // 首字母统一转大写，避免 'a' 和 'A' 被前端当成两组
        entity.setFirstLetter(dto.getFirstLetter() == null ? null : dto.getFirstLetter().toUpperCase());
        entity.setDescription(dto.getDescription());
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    private static BrandVO toVO(PmsBrand b) {
        BrandVO vo = new BrandVO();
        vo.setId(b.getId());
        vo.setName(b.getName());
        vo.setNameCn(b.getNameCn());
        vo.setLogo(b.getLogo());
        vo.setFirstLetter(b.getFirstLetter());
        vo.setDescription(b.getDescription());
        vo.setSort(b.getSort());
        vo.setStatus(b.getStatus());
        vo.setCreateTime(b.getCreateTime());
        return vo;
    }
}
