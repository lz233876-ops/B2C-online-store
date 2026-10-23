package com.shopping.web.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置。
 * <p>
 * 关键点：<b>必须显式指定序列化器</b>。Spring Data Redis 默认用
 * {@code JdkSerializationRedisSerializer}，结果是：
 * <ul>
 *   <li>写进 Redis 的 key 前面带一堆不可读的二进制前缀（如 {@code \xac\xed...}），
 *       用 redis-cli 排查问题时根本看不懂；</li>
 *   <li>存 Java 对象要求类必须实现 {@code Serializable}，且改类结构会反序列化失败。</li>
 * </ul>
 * 所以这里统一成：<b>key 用 String，value 用 JSON</b>。
 * <p>
 * 注意 {@code activateDefaultTyping} 会往 JSON 里写入 {@code @class} 字段。
 * 这是为了反序列化时能还原成正确的类型（否则 {@code List<User>} 会变成
 * {@code List<LinkedHashMap>}）。代价是 JSON 里多一个字段，且安全性上需要
 * {@code LaissezFaireSubTypeValidator} 配合——<b>不要用它反序列化不可信的外部输入</b>。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // key / hashKey 用 String，便于 redis-cli 直接阅读
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // value / hashValue 用 JSON
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }
}
