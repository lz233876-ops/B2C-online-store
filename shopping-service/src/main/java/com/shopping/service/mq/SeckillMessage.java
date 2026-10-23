package com.shopping.service.mq;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 秒杀下单消息。
 * <p>
 * <b>消息里只带「必要的最小信息」，不带商品名称、价格快照等可查询数据。</b>
 * 理由：消息是<b>异步执行的命令</b>，消费者应该以数据库为准重新读取；
 * 如果把价格塞进消息里，一旦运营在消息投递后改了秒杀价，
 * 就会出现「消息里的价」与「库里的价」两个真相，对账时无法判断谁对。
 * <p>
 * 唯一的例外是 {@code seckillPrice}：它以<b>快照</b>语义存进订单明细，
 * 但消费者仍会重新读取数据库校验，两者不一致则以库为准并记录告警。
 */
@Data
public class SeckillMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long activityId;
    private Long seckillSkuId;
    private Long skuId;
    private Long userId;
    private Integer quantity;
    /** 秒杀价快照（分） */
    private Long seckillPrice;
    /** 客户端请求唯一ID，消费端用它做幂等 */
    private String requestId;
    /** 抢购 IP，落到记录表供风控排查 */
    private String ip;
    /** 抢购时间戳（毫秒），用于排查消息延迟 */
    private Long grabTime;
}
