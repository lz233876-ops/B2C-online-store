package com.shopping.service.product.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.PmsCategoryMapper;
import com.shopping.dao.mapper.PmsSpuMapper;
import com.shopping.model.dto.CategoryDTO;
import com.shopping.model.entity.PmsCategory;
import com.shopping.model.entity.PmsSpu;
import com.shopping.model.vo.CategoryTreeVO;
import com.shopping.service.product.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 类目服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final long ROOT_PARENT_ID = 0L;
    /** 最多三级：超过三级的类目在前端导航里已无法优雅展示，筛选组合也会迅速复杂化 */
    private static final int MAX_LEVEL = 3;

    private final PmsCategoryMapper categoryMapper;
    private final PmsSpuMapper spuMapper;

    @Override
    public List<CategoryTreeVO> tree(Integer status) {
        List<PmsCategory> all = categoryMapper.selectList(
                Wrappers.<PmsCategory>lambdaQuery()
                        .eq(status != null, PmsCategory::getStatus, status)
                        .orderByAsc(PmsCategory::getSort)
                        .orderByAsc(PmsCategory::getId));
        if (all.isEmpty()) {
            return List.of();
        }

        // 两趟扫描 O(n)：先建索引，再挂载。避免「对每个节点递归查一次子节点」的 N+1
        Map<Long, CategoryTreeVO> idToNode = new LinkedHashMap<>(all.size());
        for (PmsCategory c : all) {
            idToNode.put(c.getId(), toVO(c));
        }
        List<CategoryTreeVO> roots = new ArrayList<>();
        for (CategoryTreeVO node : idToNode.values()) {
            Long parentId = node.getParentId();
            if (parentId == null || parentId == ROOT_PARENT_ID) {
                roots.add(node);
                continue;
            }
            CategoryTreeVO parent = idToNode.get(parentId);
            if (parent == null) {
                // 父节点被过滤掉了（例如只查启用状态，而父类目已禁用）：
                // 当成根节点输出，保证启用的子类目不会凭空消失
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CategoryDTO dto) {
        long parentId = normalizeParentId(dto.getParentId());
        int level = resolveLevel(parentId);
        assertSiblingNameUnique(parentId, dto.getName(), null);

        PmsCategory entity = new PmsCategory();
        entity.setParentId(parentId);
        entity.setName(dto.getName().trim());
        entity.setLevel(level);
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());
        entity.setIcon(dto.getIcon());
        entity.setBanner(dto.getBanner());
        entity.setIsShow(dto.getIsShow() == null ? 1 : dto.getIsShow());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        categoryMapper.insert(entity);
        log.info("新增类目 | id={} | name={} | level={}", entity.getId(), entity.getName(), level);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CategoryDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "修改类目时必须传 id");
        }
        PmsCategory existing = categoryMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND);
        }

        long parentId = normalizeParentId(dto.getParentId());
        // 防成环：不能把节点移动到它自己的子孙下面，否则整棵子树会从树上脱落变成孤环
        assertParentNotDescendant(dto.getId(), parentId);
        int level = resolveLevel(parentId);
        assertSiblingNameUnique(parentId, dto.getName(), dto.getId());

        PmsCategory update = new PmsCategory();
        update.setId(dto.getId());
        update.setParentId(parentId);
        update.setName(dto.getName().trim());
        update.setLevel(level);
        update.setSort(dto.getSort());
        update.setIcon(dto.getIcon());
        update.setBanner(dto.getBanner());
        update.setIsShow(dto.getIsShow());
        update.setStatus(dto.getStatus());
        categoryMapper.updateById(update);

        // 层级变了，子孙的 level 也得跟着改，否则树查询和前端渲染都会错乱
        if (!Objects.equals(existing.getLevel(), level)) {
            refreshDescendantLevel(dto.getId(), level);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PmsCategory existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND);
        }
        Long childCount = categoryMapper.selectCount(
                Wrappers.<PmsCategory>lambdaQuery().eq(PmsCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(ResultCode.CATEGORY_HAS_CHILDREN,
                    "该类目下还有 " + childCount + " 个子类目，不能删除");
        }
        Long spuCount = spuMapper.selectCount(
                Wrappers.<PmsSpu>lambdaQuery().eq(PmsSpu::getCategoryId, id));
        if (spuCount != null && spuCount > 0) {
            throw new BizException(ResultCode.CATEGORY_HAS_PRODUCT,
                    "该类目下还有 " + spuCount + " 个商品，不能删除");
        }
        categoryMapper.deleteById(id);
        log.info("删除类目 | id={} | name={}", id, existing.getName());
    }

    /* ---------------- 内部方法 ---------------- */

    private long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_PARENT_ID : parentId;
    }

    /** level 由父类目派生，不由前端传——否则会出现「parent 是二级但 level 传了 1」的数据不一致 */
    private int resolveLevel(long parentId) {
        if (parentId == ROOT_PARENT_ID) {
            return 1;
        }
        PmsCategory parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND, "父类目不存在");
        }
        int level = (parent.getLevel() == null ? 1 : parent.getLevel()) + 1;
        if (level > MAX_LEVEL) {
            throw new BizException(ResultCode.CATEGORY_LEVEL_EXCEED,
                    "父类目已是第 " + parent.getLevel() + " 级，再往下会超过 " + MAX_LEVEL + " 级上限");
        }
        return level;
    }

    private void assertSiblingNameUnique(long parentId, String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            return;
        }
        Long count = categoryMapper.selectCount(Wrappers.<PmsCategory>lambdaQuery()
                .eq(PmsCategory::getParentId, parentId)
                .eq(PmsCategory::getName, name.trim())
                .ne(excludeId != null, PmsCategory::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.CATEGORY_NAME_DUPLICATE,
                    "同级下已存在名为「" + name.trim() + "」的类目");
        }
    }

    /**
     * 校验 newParentId 不是 id 自己的子孙。
     * <p>做法：从 newParentId 顺着 parentId 往上走，如果路上遇到 id，说明成环。
     * 因为是树（每层只有一个父），向上走最多 MAX_LEVEL 步，成本极低；
     * 加一个步数上限是为了防止历史脏数据里已经存在环时死循环。
     */
    private void assertParentNotDescendant(Long id, long newParentId) {
        if (newParentId == ROOT_PARENT_ID) {
            return;
        }
        if (Objects.equals(id, newParentId)) {
            throw new BizException(ResultCode.CATEGORY_PARENT_INVALID, "不能把类目挂到它自己下面");
        }
        long cursor = newParentId;
        int guard = 0;
        while (cursor != ROOT_PARENT_ID && guard++ <= MAX_LEVEL + 1) {
            if (Objects.equals(id, cursor)) {
                throw new BizException(ResultCode.CATEGORY_PARENT_INVALID,
                        "不能把类目移动到它自己的子类目下");
            }
            PmsCategory node = categoryMapper.selectById(cursor);
            if (node == null || node.getParentId() == null) {
                break;
            }
            cursor = node.getParentId();
        }
    }

    /** 父节点层级变化后，递归修正子孙的 level */
    private void refreshDescendantLevel(Long parentId, int parentLevel) {
        List<PmsCategory> children = categoryMapper.selectList(
                Wrappers.<PmsCategory>lambdaQuery().eq(PmsCategory::getParentId, parentId));
        for (PmsCategory child : children) {
            int childLevel = parentLevel + 1;
            PmsCategory update = new PmsCategory();
            update.setId(child.getId());
            update.setLevel(childLevel);
            categoryMapper.updateById(update);
            refreshDescendantLevel(child.getId(), childLevel);
        }
    }

    private CategoryTreeVO toVO(PmsCategory c) {
        CategoryTreeVO vo = new CategoryTreeVO();
        vo.setId(c.getId());
        vo.setParentId(c.getParentId());
        vo.setName(c.getName());
        vo.setLevel(c.getLevel());
        vo.setSort(c.getSort());
        vo.setIcon(c.getIcon());
        vo.setBanner(c.getBanner());
        vo.setIsShow(c.getIsShow());
        vo.setStatus(c.getStatus());
        vo.setChildren(new ArrayList<>());
        return vo;
    }
}
