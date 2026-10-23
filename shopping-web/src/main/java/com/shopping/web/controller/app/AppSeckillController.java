package com.shopping.web.controller.app;

import com.shopping.common.result.Result;
import com.shopping.model.vo.SeckillActivityVO;
import com.shopping.model.vo.SeckillGrabResultVO;
import com.shopping.model.vo.SeckillResultVO;
import com.shopping.service.seckill.SeckillService;
import com.shopping.web.util.IpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端秒杀接口。
 * <p>
 * ⚠️ <b>抢购接口是全场压力最大的一个</b>。它的实现刻意做到：
 * 不查库、不加锁、只做 1 次 Redis 原子操作 + 1 次 MQ 投递，
 * 然后立刻返回「排队中」。订单在 MQ 消费者里异步创建。
 */
@Tag(name = "26-C端 · 秒杀", description = "秒杀活动、抢购（异步建单）、抢购结果查询")
@RestController
@RequestMapping("/api/app/seckill")
@RequiredArgsConstructor
public class AppSeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "进行中/即将开始的秒杀活动", description = "含实时剩余库存（来自 Redis）")
    @GetMapping("/list")
    public Result<List<SeckillActivityVO>> list() {
        return Result.success(seckillService.listRunning());
    }

    @Operation(summary = "秒杀活动详情", description = "公开接口，游客可浏览")
    @GetMapping("/detail/{activityId}")
    public Result<SeckillActivityVO> detail(@PathVariable Long activityId) {
        return Result.success(seckillService.detail(activityId));
    }

    /**
     * 抢购。
     * <p><b>路径参数是 {@code seckillSkuId} 而不是 skuId</b>：
     * 同一个 SKU 可以出现在多个活动里，每个活动的库存、价格、限购都是独立的，
     * 传 skuId 无法定位到具体某一场。
     * <p>返回「排队中」不代表一定抢到 —— 前端要用返回的 {@code requestId}
     * 轮询 {@code /result} 拿最终结果（订单号）。
     */
    @Operation(summary = "抢购",
            description = "同步只做 Redis 预扣 + 投递 MQ，立即返回排队中；订单异步创建")
    @PostMapping("/grab/{seckillSkuId}")
    public Result<SeckillGrabResultVO> grab(@PathVariable Long seckillSkuId, HttpServletRequest request) {
        return Result.success("已进入队列", seckillService.grab(seckillSkuId, IpUtils.getClientIp(request)));
    }

    @Operation(summary = "我的抢购结果", description = "前端轮询用：processing=true 表示名额已占、订单还在创建中")
    @GetMapping("/result")
    public Result<SeckillResultVO> result(@RequestParam Long activityId) {
        return Result.success(seckillService.queryResult(activityId));
    }
}
