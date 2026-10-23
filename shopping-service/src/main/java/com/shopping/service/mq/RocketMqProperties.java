package com.shopping.service.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "shopping.mq")
public class RocketMqProperties {

    /** NameServer 地址，如 127.0.0.1:9876 */
    private String nameServer = "127.0.0.1:9876";

    /** 秒杀下单消息的 Topic */
    private String seckillTopic = "seckill-order-topic";

    private String seckillProducerGroup = "seckill-order-producer-group";

    private String seckillConsumerGroup = "seckill-order-consumer-group";

    /**
     * 是否启用 MQ。
     * <p>设为 false 时抢购接口会直接报「秒杀通道未就绪」，
     * 用于「本机没起 Docker 但只想跑其它模块」的场景。
     */
    private boolean enabled = true;
}
