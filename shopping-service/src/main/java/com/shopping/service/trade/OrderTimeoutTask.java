package com.shopping.service.trade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时未支付订单的自动关闭任务。
 * <p>
 * <b>为什么要「定时扫描」而不是「延迟消息」——两者的取舍：</b>
 * <ul>
 *   <li>延迟消息（RocketMQ 延迟级别）：准时、不扫表，但<b>消息会丢</b>。
 *       一旦丢失，那笔订单就永远停在待付款，库存被永久占用；</li>
 *   <li>定时扫描：最坏晚一个扫描周期，但<b>只要订单还在库里就一定会被处理</b>——自愈。</li>
 * </ul>
 * 生产上的正确做法是<b>两者都要</b>：延迟消息负责「准时」，扫描负责「兜底」。
 * 本项目当前只启用扫描（RocketMQ 需要 Docker 起 broker，见 PROGRESS.md），
 * 因此功能是完整的、只是关单延迟精度取决于扫描间隔。
 * <p>
 * <b>多实例部署会不会重复关单？</b> 会扫到同一批，但不会出错——
 * {@code closeTimeoutOrder} 内部是条件更新（{@code WHERE status = 待付款}），
 * 只有一个实例能成功，其余拿到 0 行并安静跳过。
 * <b>这就是「幂等」带来的好处：正确性不依赖分布式锁，锁只需要用来省重复劳动。</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    /** 单次扫描最多处理多少笔。限制批量是为了避免一次拉起几万笔把数据库打满 */
    public static final int DEFAULT_BATCH_SIZE = 200;

    private final OrderService orderService;

    /** 默认每分钟扫一次；可通过配置调整（测试时可以调快） */
    @Scheduled(fixedDelayString = "${shopping.order.timeout-scan-interval-ms:60000}",
            initialDelayString = "${shopping.order.timeout-scan-initial-delay-ms:30000}")
    public void scheduledScan() {
        int closed = closeTimeoutOrders(DEFAULT_BATCH_SIZE);
        if (closed > 0) {
            log.info("超时关单任务完成 | 本次关闭 {} 笔", closed);
        }
    }

    /**
     * 执行一轮超时关单。定时任务与运维手工触发共用同一份逻辑。
     * <p><b>每笔订单单独捕获异常</b>：一笔失败（比如数据异常）不能让整批停摆，
     * 否则一个坏数据会让所有超时订单都关不掉。这是批处理的铁律。
     *
     * @return 实际关闭的订单数
     */
    public int closeTimeoutOrders(int limit) {
        List<Long> ids = orderService.findTimeoutOrderIds(limit);
        if (ids.isEmpty()) {
            return 0;
        }
        int closed = 0;
        for (Long id : ids) {
            try {
                if (orderService.closeTimeoutOrder(id)) {
                    closed++;
                }
            } catch (Exception e) {
                log.error("关闭超时订单失败（跳过，不影响本批其它订单）| orderId={}", id, e);
            }
        }
        return closed;
    }
}
