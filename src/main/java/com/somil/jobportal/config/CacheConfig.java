package com.somil.jobportal.config;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.somil.jobportal.entity.RecruiterJobsDto;

/**
 * Redis-backed caching for read-heavy queries. Each cache gets its own typed JSON serializer,
 * so values are stored as plain, readable JSON (inspect them with redis-cli) and read back as
 * the right Java type. If Redis is unreachable, the error handler logs it and the app falls
 * back to querying the database instead of failing the request.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String JOB_COUNT = "jobCount";
    public static final String RECRUITER_JOBS = "recruiterJobs";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer() {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        JavaType recruiterJobs = mapper.getTypeFactory().constructCollectionType(List.class, RecruiterJobsDto.class);
        return builder -> builder
                .withCacheConfiguration(JOB_COUNT,
                        json(new Jackson2JsonRedisSerializer<>(mapper, Long.class), Duration.ofMinutes(10)))
                .withCacheConfiguration(RECRUITER_JOBS,
                        json(new Jackson2JsonRedisSerializer<>(mapper, recruiterJobs), Duration.ofMinutes(5)));
    }

    private static RedisCacheConfiguration json(Jackson2JsonRedisSerializer<?> serializer, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache read failed for {}::{}, using database: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache write failed for {}::{}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed for {}::{}, entry expires by TTL: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed for {}, entries expire by TTL: {}", cache.getName(), e.getMessage());
            }
        };
    }
}
