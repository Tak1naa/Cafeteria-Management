package com.canteen.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper; // 引入 Jackson 3

import java.time.Duration;

@Configuration
@EnableCaching
@ConditionalOnProperty(name = "integration.redis.enabled", havingValue = "true")
public class RedisConfig {

    /**
     * 提供序列化器 Bean
     * 传入一个纯净的 Jackson 3 ObjectMapper 实例
     */
    @Bean
    public GenericJacksonJsonRedisSerializer jsonSerializer() {
        return new GenericJacksonJsonRedisSerializer(new ObjectMapper());
    }

    /**
     * 配置 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory, GenericJacksonJsonRedisSerializer jsonSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // key 和 HashKey 使用 String 序列化
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);

        // value 和 HashValue 使用 JSON 序列化器
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置缓存管理器（适配 @Cacheable 等注解）
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory, GenericJacksonJsonRedisSerializer jsonSerializer) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // 缓存过期时间
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
                )
                .disableCachingNullValues(); // 不缓存 null 值

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}