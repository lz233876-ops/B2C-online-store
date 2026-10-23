package com.shopping.web.controller.app;

import com.shopping.common.result.PageResult;
import com.shopping.common.result.Result;
import com.shopping.model.dto.AftersaleApplyDTO;
import com.shopping.model.dto.AftersaleReturnDTO;
import com.shopping.model.vo.AftersaleDetailVO;
import com.shopping.model.vo.AftersaleVO;
import com.shopping.service.aftersale.AftersaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端售后接口。
 * <p>入参里没有 userId；所有按 ID 的操作都在 WHERE 里带上 user_id（防水平越权）。
 */
@Tag(name = "25-C端 · 售后", description = "申请售后、我的售后单、撤销、填写退货物流")
@RestController
@RequestMapping("/api/app/aftersale")
@RequiredArgsConstructor
public class AppAftersaleController {

    private final AftersaleService aftersaleService;

    @Operation(summary = "申请售后",
            description = "按【订单明细】申请；订单须为待收货或已完成。仅退款不回补库存，退货退款才回补")
    @PostMapping("/apply")
    public Result<Long> apply(@Valid @RequestBody AftersaleApplyDTO dto) {
        return Result.success("售后申请已提交", aftersaleService.apply(dto));
    }

    @Operation(summary = "我的售后单分页")
    @GetMapping("/page")
    public Result<PageResult<AftersaleVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) Integer status) {
        return Result.success(aftersaleService.pageOfCurrentUser(pageNum, pageSize, status));
    }

    @Operation(summary = "售后单详情")
    @GetMapping("/{id}")
    public Result<AftersaleDetailVO> detail(@PathVariable Long id) {
        return Result.success(aftersaleService.detailOfCurrentUser(id));
    }

    @Operation(summary = "撤销售后申请", description = "仅「待审核」状态可撤销；订单会回到售后前的状态")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        aftersaleService.cancelByUser(id);
        return Result.<Void>success("已撤销", null);
    }

    @Operation(summary = "填写退货物流", description = "仅「退货中」状态可用")
    @PutMapping("/{id}/return")
    public Result<Void> submitReturn(@PathVariable Long id, @Valid @RequestBody AftersaleReturnDTO dto) {
        aftersaleService.submitReturn(id, dto);
        return Result.<Void>success("退货物流已提交", null);
    }
}
