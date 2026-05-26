package com.canteen.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 简单缓存配置（使用内存缓存，不需要 Redis）
 * 联调阶段使用；当 Redis 启用时此配置自动停用
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "integration.redis.enabled", havingValue = "false", matchIfMissing = true)
public class CacheConfig {

    @Bean(name = "simpleCacheManager")
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}