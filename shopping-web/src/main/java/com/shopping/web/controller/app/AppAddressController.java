package com.shopping.web.controller.app;

import com.shopping.common.result.Result;
import com.shopping.model.dto.AddressDTO;
import com.shopping.model.vo.AddressVO;
import com.shopping.service.user.UmsAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * C 端收货地址接口。
 * <p>
 * <b>本接口是「水平越权」的高危面。</b>所有操作都通过
 * {@code SecurityUtils.getAppUserId()} 取当前会员 ID，并且
 * <b>把 user_id 直接写进 SQL 的 WHERE 条件</b>，
 * 而不是「先按 id 查出来、再比对 user_id」——后者只要有一个分支忘了比对就是漏洞。
 * <p>
 * 这里也<b>不接受前端传 userId</b>：那等于把越权的钥匙交给客户端。
 */
@Tag(name = "21-C端 · 收货地址", description = "地址增删改查与设置默认（按登录会员隔离）")
@RestController
@RequestMapping("/api/app/address")
@RequiredArgsConstructor
public class AppAddressController {

    private final UmsAddressService addressService;

    @Operation(summary = "我的地址列表", description = "默认地址排在最前")
    @GetMapping("/list")
    public Result<List<AddressVO>> list() {
        return Result.success(addressService.listOfCurrentUser());
    }

    @Operation(summary = "地址详情", description = "不属于当前会员时返回「地址不存在」，避免探测他人地址 ID")
    @GetMapping("/{id}")
    public Result<AddressVO> detail(@PathVariable Long id) {
        return Result.success(addressService.detail(id));
    }

    @Operation(summary = "新增地址", description = "第一条地址自动成为默认地址")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AddressDTO dto) {
        dto.setId(null);
        return Result.success("新增成功", addressService.create(dto));
    }

    @Operation(summary = "修改地址", description = "默认状态只能转移不能取消")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody AddressDTO dto) {
        addressService.update(dto);
        return Result.<Void>success("修改成功", null);
    }

    @Operation(summary = "删除地址", description = "删除默认地址后会自动把最新一条提升为默认")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(id);
        return Result.<Void>success("删除成功", null);
    }

    @Operation(summary = "设为默认地址")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(id);
        return Result.<Void>success("已设为默认", null);
    }
}
