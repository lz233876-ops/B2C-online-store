package com.shopping.service.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.service.mq.RocketMqProperties;
import com.shopping.service.mq.SeckillMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

/**
 * 秒杀下单消费者 —— <b>削峰填谷的落点</b>。
 * <p>
 * 抢购接口把「用户 + 秒杀商品」丢进 MQ 就返回了，真正的建单（写 4 张表 + 加行锁）
 * 在这里被<b>按数据库能承受的速度</b>慢慢消费。瞬时 5000 QPS 的洪峰，
 * 出口被削成几百 TPS 的平稳流量，数据库不会被压垮。
 * <p>
 * <b>三条必须守住的规则：</b>
 * <ol>
 *   <li><b>幂等命中不回滚 Redis</b>：重复消息或重复抢购说明「这笔名额已经被正常消费过」，
 *       再回滚就把库存凭空加回去了 —— 那才是真正的超卖来源；</li>
 *   <li><b>建单失败必须回滚 Redis</b>：否则那件商品被永久占住，卖不出去也查不到；</li>
 *   <li><b>单条消息失败不能让整批重试</b>：一批里有一条坏数据，逐条捕获，
 *       其余正常消费，最终返回 CONSUME_SUCCESS。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final RocketMqProperties properties;
    private final ObjectMapper objectMapper;
    private final SeckillOrderCreator orderCreator;
    private final SeckillRedisSupport redisSupport;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.warn("MQ 已禁用，秒杀消费者不启动");
            return;
        }
        try {
            DefaultMQPushConsumer c = new DefaultMQPushConsumer(properties.getSeckillConsumerGroup());
            c.setNamesrvAddr(properties.getNameServer());
            // 消费位点：从最早开始，保证重启期间积压的消息也会被处理
            c.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
            // 建单是 IO 密集（4~5 条 SQL），线程数给足才能跟上洪峰；
            // 上限受数据库连接池约束，调太大只会让请求排队在连接池上
            c.setConsumeThreadMin(20);
            c.setConsumeThreadMax(64);
            c.setConsumeMessageBatchMaxSize(1);
            c.subscribe(properties.getSeckillTopic(), "*");
            c.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                for (MessageExt msg : msgs) {
                    handleOne(msg);
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            c.start();
            this.consumer = c;
            log.info("秒杀消费者启动成功 | nameServer={} | topic={} | group={}",
                    properties.getNameServer(), properties.getSeckillTopic(),
                    properties.getSeckillConsumerGroup());
        } catch (Exception e) {
            // 和 Producer 一样：MQ 不可用不该让应用起不来
            log.error("秒杀消费者启动失败（MQ 不可用）| nameServer={} | 原因={}",
                    properties.getNameServer(), e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
            log.info("秒杀消费者已关闭");
        }
    }

    private void handleOne(MessageExt msg) {
        SeckillMessage payload = null;
        try {
            payload = objectMapper.readValue(msg.getBody(), SeckillMessage.class);
            SeckillOrderResult result = orderCreator.create(payload);
            if (result.created()) {
                log.info("秒杀订单已创建 | orderNo={} | requestId={}", result.orderNo(), payload.getRequestId());
            } else {
                // ⚠️ 幂等命中：这条名额已经被正常消费过，【绝不能】回滚 Redis
                log.info("秒杀消息幂等命中 | requestId={} | 原因={}", payload.getRequestId(), result.reason());
            }
        } catch (Exception e) {
            // 建单失败 → 事务已回滚（记录/订单/库存流水都没留下）→ 必须把 Redis 占位还回去，
            // 否则这件商品永远卖不出去。
            if (payload != null) {
                redisSupport.rollback(payload.getSeckillSkuId(), payload.getActivityId(),
                        payload.getUserId(), payload.getQuantity() == null ? 1 : payload.getQuantity());
                log.error("秒杀建单失败，已回滚 Redis 占位 | requestId={} | userId={} | 原因={}",
                        payload.getRequestId(), payload.getUserId(), e.getMessage(), e);
            } else {
                // 连消息都解析不了，无法补偿 —— 必须人工介入
                log.error("⚠️ 秒杀消息解析失败，无法补偿，需人工核对 | msgId={}",
                        msg.getMsgId(), e);
            }
        }
    }
}
