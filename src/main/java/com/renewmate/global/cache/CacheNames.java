package com.renewmate.global.cache;

/**
 * 사용자별 지출 집계 캐시. 모두 userId를 키로 사용한다.
 */
public final class CacheNames {

    public static final String STATISTICS_SUMMARY = "statisticsSummary";
    public static final String STATISTICS_SERVICES = "statisticsServices";
    public static final String STATISTICS_CATEGORIES = "statisticsCategories";
    public static final String DASHBOARD_SUMMARY = "dashboardSummary";

    private CacheNames() {
    }
}
