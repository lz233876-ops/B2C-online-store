package com.shopping.web.controller.app;

import com.shopping.common.result.Result;
import com.shopping.model.dto.CartAddDTO;
import com.shopping.model.dto.CartSelectDTO;
import com.shopping.model.dto.CartUpdateDTO;
import com.shopping.model.vo.CartVO;
import com.shopping.service.cart.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端购物车接口。
 * <p>
 * 全部需要会员登录（不在白名单里），且<b>入参中没有任何 userId</b>——
 * 购物车天然按登录会员隔离，越权无从下手。
 */
@Tag(name = "22-C端 · 购物车", description = "加购、改数量、勾选、删除、查看")
@RestController
@RequestMapping("/api/app/cart")
@RequiredArgsConstructor
public class AppCartController {

    private final CartService cartService;

    @Operation(summary = "购物车列表", description = "含失效项标记（下架/删除/售罄/库存不足）；汇总只统计已勾选且有效的项")
    @GetMapping("/list")
    public Result<CartVO> list() {
        return Result.success(cartService.listOfCurrentUser());
    }

    @Operation(summary = "加入购物车", description = "同一 SKU 重复加购会累加数量；会校验商品在售与库存")
    @PostMapping("/add")
    public Result<Long> add(@Valid @RequestBody CartAddDTO dto) {
        return Result.success("已加入购物车", cartService.add(dto));
    }

    @Operation(summary = "修改数量", description = "数量传 0 等价于删除该项")
    @PutMapping("/quantity")
    public Result<Void> updateQuantity(@Valid @RequestBody CartUpdateDTO dto) {
        cartService.updateQuantity(dto);
        return Result.<Void>success("已更新", null);
    }

    @Operation(summary = "勾选 / 取消勾选", description = "ids 传空表示操作全部（全选/取消全选）")
    @PutMapping("/selected")
    public Result<Void> updateSelected(@Valid @RequestBody CartSelectDTO dto) {
        cartService.updateSelected(dto);
        return Result.<Void>success("已更新", null);
    }

    @Operation(summary = "批量删除购物车项")
    @DeleteMapping("/remove")
    public Result<Void> remove(@RequestParam List<Long> ids) {
        cartService.remove(ids);
        return Result.<Void>success("已删除", null);
    }

    @Operation(summary = "购物车商品种类数", description = "用于前端角标")
    @GetMapping("/count")
    public Result<Integer> count() {
        return Result.success(cartService.countOfCurrentUser());
    }
}
