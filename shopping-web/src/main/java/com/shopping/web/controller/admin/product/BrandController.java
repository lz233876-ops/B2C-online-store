package com.shopping.web.controller.admin.product;

import com.shopping.common.result.PageResult;
import com.shopping.common.result.Result;
import com.shopping.model.dto.BrandDTO;
import com.shopping.model.query.BrandQuery;
import com.shopping.model.vo.BrandVO;
import com.shopping.service.product.BrandService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 品牌管理接口。权限粒度说明见 {@link CategoryController}。
 */
@Tag(name = "11-商品中心 · 品牌", description = "品牌查询与维护")
@RestController
@RequestMapping("/api/admin/product/brand")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @Operation(summary = "品牌分页查询", description = "name 会同时模糊匹配中英文名")
    @PreAuthorize("@ps.hasPerm('product:brand:list')")
    @GetMapping("/page")
    public Result<PageResult<BrandVO>> page(BrandQuery query) {
        return Result.success(brandService.page(query));
    }

    @Operation(summary = "启用品牌列表", description = "供商品表单下拉框使用，不含分页")
    @PreAuthorize("@ps.hasPerm('product:brand:list')")
    @GetMapping("/list")
    public Result<List<BrandVO>> listEnabled() {
        return Result.success(brandService.listEnabled());
    }

    @Operation(summary = "新增品牌")
    @PreAuthorize("@ps.hasPerm('product:brand:list')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BrandDTO dto) {
        dto.setId(null);
        return Result.success("新增成功", brandService.create(dto));
    }

    @Operation(summary = "修改品牌")
    @PreAuthorize("@ps.hasPerm('product:brand:list')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody BrandDTO dto) {
        brandService.update(dto);
        return Result.<Void>success("修改成功", null);
    }

    @Operation(summary = "删除品牌", description = "品牌下有商品时拒绝删除")
    @PreAuthorize("@ps.hasPerm('product:brand:list')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        brandService.delete(id);
        return Result.<Void>success("删除成功", null);
    }
}
