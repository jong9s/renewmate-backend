package com.renewmate.global.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.cache.annotation.CacheEvict;

/**
 * 구독 데이터가 바뀌는 메서드에 붙여 해당 사용자의 지출 집계 캐시를 비운다.
 * 메서드에 {@code userId} 파라미터가 있어야 한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@CacheEvict(
        cacheNames = {
                CacheNames.STATISTICS_SUMMARY,
                CacheNames.STATISTICS_SERVICES,
                CacheNames.STATISTICS_CATEGORIES,
                CacheNames.DASHBOARD_SUMMARY
        },
        key = "#userId"
)
public @interface EvictUserSpendingCache {
}
