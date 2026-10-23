package com.shopping.web.controller.app;

import com.shopping.common.result.PageResult;
import com.shopping.common.result.Result;
import com.shopping.model.vo.AppSpuDetailVO;
import com.shopping.model.vo.AppSpuVO;
import com.shopping.service.product.AppProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端商品浏览接口（<b>免登录</b>，游客可逛）。
 * <p>
 * ⚠️ <b>返回的 VO 不含成本价。</b>这是与后台商品接口最重要的区别，
 * 详见 {@code AppSpuVO} 的类注释。
 */
@Tag(name = "27-C端 · 商品浏览", description = "商品列表、商品详情（不含成本价）")
@RestController
@RequestMapping("/api/app/product")
@RequiredArgsConstructor
public class AppProductController {

    private final AppProductService appProductService;

    @Operation(summary = "商品列表", description = "仅返回「审核通过 + 已上架」的商品；可按类目筛选、按名称搜索")
    @GetMapping("/list")
    public Result<PageResult<AppSpuVO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) String keyword) {
        return Result.success(appProductService.page(pageNum, pageSize, categoryId, keyword));
    }

    @Operation(summary = "商品详情", description = "含 SKU、规格属性、相册与详情；不含成本价")
    @GetMapping("/{spuId}")
    public Result<AppSpuDetailVO> detail(@PathVariable Long spuId) {
        return Result.success(appProductService.detail(spuId));
    }
}
