package com.renewmate.statistics.dto;

import java.math.BigDecimal;

public record StatisticsSummaryResponse(
        long activeSubscriptionCount,
        BigDecimal monthlyTotalAmount,
        BigDecimal annualTotalAmount,
        BigDecimal averageMonthlyAmount
) {
}