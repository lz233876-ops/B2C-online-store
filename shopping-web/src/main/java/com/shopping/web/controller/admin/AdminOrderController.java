package com.shopping.web.controller.admin;

import com.shopping.common.result.PageResult;
import com.shopping.common.result.Result;
import com.shopping.model.vo.OrderDetailVO;
import com.shopping.model.vo.OrderVO;
import com.shopping.service.trade.OrderService;
import com.shopping.service.trade.OrderTimeoutTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
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
 * 管理端订单接口。
 * <p>权限码沿用种子数据里已有的 {@code trade:order:*}（见 {@code sys_menu} 300 段）。
 */
@Tag(name = "12-订单管理", description = "订单查询、发货、超时关单")
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderTimeoutTask orderTimeoutTask;

    @Operation(summary = "订单分页", description = "可按状态与订单号筛选；看到全部会员的订单")
    @GetMapping("/page")
    @PreAuthorize("@ps.hasPerm('trade:order:list')")
    public Result<PageResult<OrderVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) String orderNo) {
        return Result.success(orderService.adminPage(pageNum, pageSize, status, orderNo));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ps.hasPerm('trade:order:list')")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.success(orderService.adminDetail(id));
    }

    @Operation(summary = "订单发货", description = "待发货 → 待收货；物流单号必填")
    @PutMapping("/{id}/deliver")
    @PreAuthorize("@ps.hasPerm('trade:order:deliver')")
    public Result<Void> deliver(@PathVariable Long id, @RequestBody DeliverDTO dto) {
        orderService.deliver(id, dto.getDeliveryCompany(), dto.getDeliveryNo());
        return Result.<Void>success("发货成功", null);
    }

    /**
     * 手工触发一轮超时关单。
     * <p>存在的意义有两个：① 运维在停止定时任务时可以手动补一轮；
     * ② <b>验收脚本不必等 30 分钟</b>——把订单的 {@code auto_close_time} 改到过去，
     * 再调这个接口就能立刻验证关单链路（含库存释放）。
     */
    @Operation(summary = "手工触发超时关单", description = "立即扫描并关闭已超时未支付的订单，返回关闭笔数")
    @PostMapping("/close-timeout")
    @PreAuthorize("@ps.hasPerm('trade:order:close')")
    public Result<Integer> closeTimeout(@RequestParam(defaultValue = "200") int limit) {
        return Result.success("已执行", orderTimeoutTask.closeTimeoutOrders(limit));
    }

    @Data
    public static class DeliverDTO {

        @io.swagger.v3.oas.annotations.media.Schema(description = "物流公司", requiredMode =
                io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "物流公司不能为空")
        private String deliveryCompany;

        @io.swagger.v3.oas.annotations.media.Schema(description = "物流单号", requiredMode =
                io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "物流单号不能为空")
        private String deliveryNo;
    }
}
