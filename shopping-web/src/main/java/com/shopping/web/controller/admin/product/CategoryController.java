package com.shopping.web.controller.admin.product;

import com.shopping.common.result.Result;
import com.shopping.model.dto.CategoryDTO;
import com.shopping.model.vo.CategoryTreeVO;
import com.shopping.service.product.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 类目管理接口。
 * <p>
 * ⚠️ <b>权限粒度说明（已知简化）</b>：初始化数据里主数据模块只定义了
 * {@code product:category:list} 这一个权限点，没有拆出 {@code :add} / {@code :edit} / {@code :delete}。
 * 所以本类的<b>读写共用同一个权限</b>。
 * <p>
 * 生产环境的正确做法是给每个写操作单独建权限点（这样「只读运营」和「可改类目的运营」
 * 才是两个角色），本项目的简化点在此显式记录，而不是假装没这回事。
 */
@Tag(name = "10-商品中心 · 类目", description = "类目树查询与维护")
@RestController
@RequestMapping("/api/admin/product/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "类目树", description = "status 不传返回全部（后台管理）；传 1 只返回启用的（前台）")
    @PreAuthorize("@ps.hasPerm('product:category:list')")
    @GetMapping("/tree")
    public Result<List<CategoryTreeVO>> tree(@RequestParam(required = false) Integer status) {
        return Result.success(categoryService.tree(status));
    }

    @Operation(summary = "新增类目", description = "层级由父类目派生，最多三级")
    @PreAuthorize("@ps.hasPerm('product:category:list')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CategoryDTO dto) {
        // 新增时不允许传 id，避免前端复用一个 DTO 造成「新增变成覆盖」
        dto.setId(null);
        return Result.success("新增成功", categoryService.create(dto));
    }

    @Operation(summary = "修改类目")
    @PreAuthorize("@ps.hasPerm('product:category:list')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody CategoryDTO dto) {
        categoryService.update(dto);
        return Result.<Void>success("修改成功", null);
    }

    @Operation(summary = "删除类目", description = "有子类目或有商品时拒绝删除")
    @PreAuthorize("@ps.hasPerm('product:category:list')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.<Void>success("删除成功", null);
    }
}
