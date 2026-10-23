package com.shopping.web.controller.admin;

import com.shopping.common.result.PageResult;
import com.shopping.common.result.Result;
import com.shopping.model.dto.AftersaleAuditDTO;
import com.shopping.model.vo.AftersaleDetailVO;
import com.shopping.model.vo.AftersaleVO;
import com.shopping.service.aftersale.AftersaleService;
import com.shopping.service.aftersale.AftersaleTimeoutTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端售后接口。
 * <p>
 * ⚠️ <b>权限粒度说明（技术债）：</b>种子数据里售后只有 {@code trade:aftersale:list}
 * 一个权限点，所以「列表 / 详情 / 审核 / 确认收货 / 手工关单」目前共用它。
 * 真实系统应当把「审核」与「退款」拆成独立的按钮级权限
 * （{@code trade:aftersale:audit} / {@code trade:aftersale:refund}）——
 * 因为<b>能看和能退钱是完全不同的风险等级</b>。已记入 PROGRESS.md §七。
 */
@Tag(name = "13-售后管理", description = "售后查询、审核、确认收货退款、超时关单")
@RestController
@RequestMapping("/api/admin/aftersale")
@RequiredArgsConstructor
public class AdminAftersaleController {

    private final AftersaleService aftersaleService;
    private final AftersaleTimeoutTask aftersaleTimeoutTask;

    @Operation(summary = "售后分页", description = "可按状态筛选；看到全部会员的售后单")
    @GetMapping("/page")
    @PreAuthorize("@ps.hasPerm('trade:aftersale:list')")
    public Result<PageResult<AftersaleVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) Integer status) {
        return Result.success(aftersaleService.adminPage(pageNum, pageSize, status));
    }

    @Operation(summary = "售后详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ps.hasPerm('trade:aftersale:list')")
    public Result<AftersaleDetailVO> detail(@PathVariable Long id) {
        return Result.success(aftersaleService.adminDetail(id));
    }

    @Operation(summary = "审核售后",
            description = "同意+仅退款 → 立即退款完成；同意+退货退款 → 进入退货中；拒绝 → 订单回到售后前状态")
    @PutMapping("/{id}/audit")
    @PreAuthorize("@ps.hasPerm('trade:aftersale:list')")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody AftersaleAuditDTO dto) {
        aftersaleService.audit(id, dto);
        return Result.<Void>success("审核完成", null);
    }

    @Operation(summary = "确认收到退货并退款", description = "仅「退货中」状态可用；会退款并回补库存")
    @PutMapping("/{id}/receive")
    @PreAuthorize("@ps.hasPerm('trade:aftersale:list')")
    public Result<Void> confirmReceive(@PathVariable Long id) {
        aftersaleService.confirmReceive(id);
        return Result.<Void>success("已确认收货并退款", null);
    }

    @Operation(summary = "手工触发超时关单", description = "立即扫描并关闭超时未审核的售后单，返回关闭笔数")
    @PostMapping("/close-timeout")
    @PreAuthorize("@ps.hasPerm('trade:aftersale:list')")
    public Result<Integer> closeTimeout(@RequestParam(defaultValue = "100") int limit) {
        return Result.success("已执行", aftersaleTimeoutTask.closeTimeout(limit));
    }
}
