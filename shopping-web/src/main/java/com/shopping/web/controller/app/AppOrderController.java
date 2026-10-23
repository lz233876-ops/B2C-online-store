package com.shopping.web.controller.app;

import com.shopping.common.result.PageResult;
import com.shopping.common.result.Result;
import com.shopping.model.dto.OrderSubmitDTO;
import com.shopping.model.vo.OrderDetailVO;
import com.shopping.model.vo.OrderSubmitVO;
import com.shopping.model.vo.OrderVO;
import com.shopping.service.trade.OrderService;
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
 * C 端订单接口。
 * <p>入参里没有 userId；所有按 ID 的操作都在 SQL 的 WHERE 里带上 user_id（防水平越权）。
 */
@Tag(name = "23-C端 · 订单", description = "提交订单、我的订单、详情、取消、确认收货")
@RestController
@RequestMapping("/api/app/order")
@RequiredArgsConstructor
public class AppOrderController {

    private final OrderService orderService;

    /**
     * 提交订单。
     * <p>⚠️ 这是<b>幂等性最弱</b>的接口：用户重复点击会创建两笔订单。
     * 工业界的做法是前端带一次性 token（防重令牌），服务端用 Redis 校验并消费掉。
     * 本项目留到 Phase 3 与秒杀一起做（秒杀的「一人一单」是同一个问题的更强版本）。
     */
    @Operation(summary = "提交订单",
            description = "从购物车【已勾选】的项下单；锁库存、建订单、清购物车在同一事务内完成")
    @PostMapping("/submit")
    public Result<OrderSubmitVO> submit(@Valid @RequestBody OrderSubmitDTO dto) {
        return Result.success("下单成功", orderService.submit(dto));
    }

    @Operation(summary = "我的订单分页", description = "status 不传表示全部；返回中含倒计时剩余秒数")
    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) Integer status) {
        return Result.success(orderService.pageOfCurrentUser(pageNum, pageSize, status));
    }

    @Operation(summary = "订单详情", description = "含商品明细与状态流转记录")
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.success(orderService.detailOfCurrentUser(id));
    }

    @Operation(summary = "取消订单", description = "只能取消待付款的订单；已付款请走售后")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        orderService.cancel(id, reason);
        return Result.<Void>success("订单已取消", null);
    }

    @Operation(summary = "确认收货", description = "待收货 → 已完成")
    @PutMapping("/{id}/receive")
    public Result<Void> confirmReceive(@PathVariable Long id) {
        orderService.confirmReceive(id);
        return Result.<Void>success("已确认收货", null);
    }
}
