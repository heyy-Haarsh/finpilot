package com.finpilot.common.cache;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import java.util.concurrent.TimeUnit;


@Configuration
public class CacheConfig {

    public static final String AI_REVIEWS = "aiReviews";

    @Bean
    public CacheManager cacheManager() {

        CaffeineCacheManager cacheManager = new CaffeineCacheManager(AI_REVIEWS);

        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(1000)
        );

        return cacheManager;
    }
}
