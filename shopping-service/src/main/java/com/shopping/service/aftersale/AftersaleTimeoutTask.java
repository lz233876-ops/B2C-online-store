package com.shopping.service.aftersale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 售后超时未处理的自动关闭任务。
 * <p>
 * <b>为什么「扫描」和「关闭」要拆成两个类/两个方法：</b>
 * Spring 的 {@code @Transactional} 靠代理生效。<b>同类内部方法自调用会绕过代理</b>，
 * 事务注解<b>静默失效</b>——不报错，只表现为「一笔失败把整批都回滚了」。
 * 所以本类只做「扫描 + 循环」，每笔都通过接口调用 {@link AftersaleService#closeOne(Long)}，
 * 让代理生效、每笔一个事务。<b>这是批处理的基本功，也是最容易踩的坑。</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AftersaleTimeoutTask {

    /** 单次最多处理多少笔，避免一次拉起太多把数据库打满 */
    public static final int DEFAULT_BATCH_SIZE = 100;

    private final AftersaleService aftersaleService;

    /** 默认每小时扫一次：售后不像订单那么急，7 天窗口下每小时足够 */
    @Scheduled(fixedDelayString = "${shopping.aftersale.scan-interval-ms:3600000}",
            initialDelayString = "${shopping.aftersale.scan-initial-delay-ms:60000}")
    public void scheduledScan() {
        int closed = closeTimeout(DEFAULT_BATCH_SIZE);
        if (closed > 0) {
            log.info("售后超时关闭任务完成 | 本次关闭 {} 笔", closed);
        }
    }

    /**
     * 执行一轮超时关闭。定时任务与管理端手工触发共用。
     *
     * @return 实际关闭数量
     */
    public int closeTimeout(int limit) {
        List<Long> ids = aftersaleService.findTimeoutAftersaleIds(limit);
        if (ids.isEmpty()) {
            return 0;
        }
        int closed = 0;
        for (Long id : ids) {
            try {
                if (aftersaleService.closeOne(id)) {
                    closed++;
                }
            } catch (Exception e) {
                // 单笔失败不影响其它笔
                log.error("自动关闭售后失败（跳过，不影响本批）| aftersaleId={}", id, e);
            }
        }
        return closed;
    }
}
