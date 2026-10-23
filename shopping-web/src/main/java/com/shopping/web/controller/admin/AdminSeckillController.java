package com.shopping.web.controller.admin;

import com.shopping.common.result.Result;
import com.shopping.service.seckill.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端秒杀运维接口。
 * <p>存在的主要目的是<b>可观测与可手动干预</b>：预热是否跑过、缓存里有多少个秒杀商品。
 * 压测前可以手工触发一次预热，避免「应用刚启动、预热还没跑」导致第一批请求全部失败。
 */
@Tag(name = "14-秒杀运维", description = "手动预热、缓存观察")
@RestController
@RequestMapping("/api/admin/seckill")
@RequiredArgsConstructor
public class AdminSeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "手动预热", description = "把进行中/即将开始的活动的库存写入 Redis，并刷新进程内缓存")
    @PostMapping("/warm-up")
    @PreAuthorize("@ps.hasPerm('trade:order:list')")
    public Result<Integer> warmUp() {
        return Result.success("预热完成", seckillService.warmUp());
    }

    @Operation(summary = "观察进程内缓存条目数", description = "运维排查用：正常情况下应等于进行中活动的秒杀商品数")
    @GetMapping("/cached-count")
    @PreAuthorize("@ps.hasPerm('trade:order:list')")
    public Result<Integer> cachedCount() {
        return Result.success(seckillService.cachedSkuCount());
    }
}
