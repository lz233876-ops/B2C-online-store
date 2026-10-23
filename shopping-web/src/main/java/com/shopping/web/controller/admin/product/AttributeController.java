package com.shopping.web.controller.admin.product;

import com.shopping.common.result.PageResult;
import com.shopping.common.result.Result;
import com.shopping.model.dto.AttributeDTO;
import com.shopping.model.query.AttributeQuery;
import com.shopping.model.vo.AttributeVO;
import com.shopping.service.product.AttributeService;
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
 * 属性管理接口。权限粒度说明见 {@link CategoryController}。
 */
@Tag(name = "12-商品中心 · 属性", description = "规格属性 / 参数属性与属性值维护")
@RestController
@RequestMapping("/api/admin/product/attribute")
@RequiredArgsConstructor
public class AttributeController {

    private final AttributeService attributeService;

    @Operation(summary = "属性分页查询", description = "返回的每条属性内联其属性值列表")
    @PreAuthorize("@ps.hasPerm('product:attribute:list')")
    @GetMapping("/page")
    public Result<PageResult<AttributeVO>> page(AttributeQuery query) {
        return Result.success(attributeService.page(query));
    }

    @Operation(summary = "属性详情")
    @PreAuthorize("@ps.hasPerm('product:attribute:list')")
    @GetMapping("/{id}")
    public Result<AttributeVO> detail(@PathVariable Long id) {
        return Result.success(attributeService.detail(id));
    }

    @Operation(summary = "按类目查规格属性",
            description = "只返回 type=1 的规格属性及其属性值。商品表单用它渲染规格区并计算 SKU 笛卡尔积")
    @PreAuthorize("@ps.hasPerm('product:attribute:list')")
    @GetMapping("/spec")
    public Result<List<AttributeVO>> listSpecByCategory(@RequestParam Long categoryId) {
        return Result.success(attributeService.listSpecByCategory(categoryId));
    }

    @Operation(summary = "新增属性",
            description = "聚合保存：属性本体 + 属性值一次提交。规格属性必须至少有一个属性值")
    @PreAuthorize("@ps.hasPerm('product:attribute:list')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AttributeDTO dto) {
        dto.setId(null);
        return Result.success("新增成功", attributeService.create(dto));
    }

    @Operation(summary = "修改属性", description = "属性值增量同步；已被 SKU 使用的值不能删除")
    @PreAuthorize("@ps.hasPerm('product:attribute:list')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody AttributeDTO dto) {
        attributeService.update(dto);
        return Result.<Void>success("修改成功", null);
    }

    @Operation(summary = "删除属性", description = "被商品或 SKU 引用时拒绝删除")
    @PreAuthorize("@ps.hasPerm('product:attribute:list')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        attributeService.delete(id);
        return Result.<Void>success("删除成功", null);
    }
}
