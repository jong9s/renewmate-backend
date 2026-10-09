package com.renewmate.global.cache;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 단일 EC2 인스턴스 구성이라 Redis 없이 로컬 캐시를 사용한다.
 * 정확성은 구독 변경 시 무효화로 보장하고, TTL은 메모리 상한과 날짜가 바뀌는 값(결제 예정 개수)의 갱신용이다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${app.cache.spending.ttl-minutes:10}") long ttlMinutes,
            @Value("${app.cache.spending.maximum-size:10000}") long maximumSize
    ) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                CacheNames.STATISTICS_SUMMARY,
                CacheNames.STATISTICS_SERVICES,
                CacheNames.STATISTICS_CATEGORIES,
                CacheNames.DASHBOARD_SUMMARY
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .maximumSize(maximumSize)
                .recordStats());

        // 커밋 전에 비우면 그 사이 다른 요청이 변경 전 데이터를 다시 캐시할 수 있으므로 커밋 후에 비운다
        return new TransactionAwareCacheManagerProxy(cacheManager);
    }
}
