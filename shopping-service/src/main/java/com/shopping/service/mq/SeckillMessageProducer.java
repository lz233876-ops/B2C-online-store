package com.shopping.service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Component;

/**
 * 秒杀消息生产者。
 * <p>
 * <b>手工管理生命周期，不用 starter。</b>这样：
 * <ul>
 *   <li>启动失败时的报错直接指向我的代码，不会被自动装配层包一层；</li>
 *   <li>可以做「<b>MQ 不可用时应用照样能启动</b>」这种降级 ——
 *       本地没起 Docker 时其它模块仍然可以正常开发调试（见 {@link #available}）。</li>
 * </ul>
 * <p>
 * <b>为什么用同步发送（{@code producer.send}）而不是 {@code sendOneway}：</b>
 * 抢购成功后必须确认「消息真的进了 Broker」，否则 Redis 预扣了库存但消息丢了，
 * 那件商品就永远卖不出去（库存被永久占住）。同步发送多花几毫秒，
 * 换来的是<b>可以据此回滚 Redis</b>。这是正确的取舍。
 */
@Slf4j
@Component
public class SeckillMessageProducer {

    private final RocketMqProperties properties;
    private final ObjectMapper objectMapper;

    private DefaultMQProducer producer;

    /** MQ 是否可用。不可用时抢购接口会明确报错，而不是假装成功 */
    private volatile boolean available = false;

    public SeckillMessageProducer(RocketMqProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.warn("MQ 已禁用（shopping.mq.enabled=false），秒杀抢购接口将返回「通道未就绪」");
            return;
        }
        try {
            DefaultMQProducer p = new DefaultMQProducer(properties.getSeckillProducerGroup());
            p.setNamesrvAddr(properties.getNameServer());
            p.setSendMsgTimeout(3000);
            // 发送失败重试 2 次：秒杀是瞬时流量，网络抖动不该让用户白抢
            p.setRetryTimesWhenSendFailed(2);
            p.start();
            this.producer = p;
            this.available = true;
            log.info("秒杀 Producer 启动成功 | nameServer={} | topic={}",
                    properties.getNameServer(), properties.getSeckillTopic());
        } catch (Exception e) {
            // ⚠️ 这里刻意【不】抛异常：MQ 没起来不该让整个应用起不来。
            //    降级为「抢购不可用」，其它模块（商品/订单/支付/售后）照常工作。
            this.available = false;
            log.error("秒杀 Producer 启动失败（MQ 不可用，抢购接口将降级）| nameServer={} | 原因={}",
                    properties.getNameServer(), e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            producer.shutdown();
            log.info("秒杀 Producer 已关闭");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * 发送秒杀下单消息（同步）。
     *
     * @throws IllegalStateException MQ 不可用或发送失败 —— 调用方必须据此<b>回滚 Redis 预扣</b>
     */
    public void send(SeckillMessage message) {
        if (!available || producer == null) {
            throw new IllegalStateException("秒杀消息通道未就绪");
        }
        try {
            byte[] body = objectMapper.writeValueAsBytes(message);
            Message msg = new Message(properties.getSeckillTopic(), "GRAB", body);
            // 用 requestId 做业务 key，便于在 Broker 上按用户请求排查
            msg.setKeys(message.getRequestId());
            SendResult result = producer.send(msg);
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("消息发送未成功：" + (result == null ? "null" : result.getSendStatus()));
            }
            log.info("秒杀消息已投递 | userId={} | activityId={} | requestId={} | msgId={}",
                    message.getUserId(), message.getActivityId(), message.getRequestId(),
                    result.getMsgId());
        } catch (Exception e) {
            throw new IllegalStateException("秒杀消息发送失败：" + e.getMessage(), e);
        }
    }
}
