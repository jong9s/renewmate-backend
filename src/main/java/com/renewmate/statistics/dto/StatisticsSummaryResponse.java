package com.renewmate.statistics.dto;

import java.util.List;

import com.renewmate.subscription.dto.CurrencyAmountResponse;

public record StatisticsSummaryResponse(
        long activeSubscriptionCount,
        List<CurrencyAmountResponse> totals
) {
}