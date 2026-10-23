package com.shopping.web.controller.admin.product;

import com.shopping.common.result.PageResult;
import com.shopping.common.result.Result;
import com.shopping.model.dto.SpuSaveDTO;
import com.shopping.model.query.SpuQuery;
import com.shopping.model.vo.SpuDetailVO;
import com.shopping.model.vo.SpuVO;
import com.shopping.service.product.SpuService;
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

/**
 * 商品（SPU）管理接口。
 * <p>
 * 权限点与其它主数据模块不同：SPU <b>有完整的增删改查权限点</b>
 * （{@code product:spu:list|add|edit|delete|publish}），所以这里能按操作细分。
 * 类目/品牌/属性只有 {@code :list}，粒度说明见 {@link CategoryController}。
 */
@Tag(name = "13-商品中心 · 商品(SPU)", description = "SPU 聚合保存：商品主体 + 规格属性 + 图片 + SKU")
@RestController
@RequestMapping("/api/admin/product/spu")
@RequiredArgsConstructor
public class SpuController {

    private final SpuService spuService;

    @Operation(summary = "商品分页查询",
            description = "categoryId 传父类目时会自动包含其全部子孙类目的商品；名字会批量补齐，不是 N+1 查询")
    @PreAuthorize("@ps.hasPerm('product:spu:list')")
    @GetMapping("/page")
    public Result<PageResult<SpuVO>> page(SpuQuery query) {
        return Result.success(spuService.page(query));
    }

    @Operation(summary = "商品详情",
            description = "返回商品主体 + 规格属性 + 参数属性 + 图片 + SKU 列表（含每个 SKU 的规格组合）")
    @PreAuthorize("@ps.hasPerm('product:spu:list')")
    @GetMapping("/{id}")
    public Result<SpuDetailVO> detail(@PathVariable Long id) {
        return Result.success(spuService.detail(id));
    }

    @Operation(summary = "新增商品（聚合保存）",
            description = "一次提交：商品主体 + 规格属性 + 参数属性 + 图片 + SKU 列表。"
                    + "SKU 的笛卡尔积由前端计算后提交，服务端负责校验合法性；"
                    + "所有写操作在一个事务内完成。")
    @PreAuthorize("@ps.hasPerm('product:spu:add')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SpuSaveDTO dto) {
        dto.setId(null);
        return Result.success("新增成功", spuService.save(dto));
    }

    @Operation(summary = "修改商品（聚合保存）",
            description = "SKU 支持增量同步：带 id 的按 id 匹配；不带 id 的按规格组合匹配，"
                    + "从而保住已有 SKU 的价格与销量；库里多出来的组合会被逻辑删除。")
    @PreAuthorize("@ps.hasPerm('product:spu:edit')")
    @PutMapping
    public Result<Long> update(@Valid @RequestBody SpuSaveDTO dto) {
        if (dto.getId() == null) {
            return Result.failure(com.shopping.common.result.ResultCode.BAD_REQUEST, "修改商品时必须传 id");
        }
        return Result.success("修改成功", spuService.save(dto));
    }

    @Operation(summary = "上下架",
            description = "status=1 上架，0 下架。改的是 pms_spu.status（不是审核状态）；"
                    + "上架要求审核已通过，且 SKU 的启用状态会跟随变化")
    @PreAuthorize("@ps.hasPerm('product:spu:publish')")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        spuService.updateStatus(id, status);
        return Result.<Void>success(status == 1 ? "已上架" : "已下架", null);
    }

    @Operation(summary = "删除商品", description = "逻辑删除，连带删除其 SKU、规格组合与图片")
    @PreAuthorize("@ps.hasPerm('product:spu:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        spuService.delete(id);
        return Result.<Void>success("删除成功", null);
    }
}
