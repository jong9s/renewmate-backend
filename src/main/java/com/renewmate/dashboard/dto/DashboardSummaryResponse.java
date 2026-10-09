package com.renewmate.dashboard.dto;

import java.util.List;

import com.renewmate.subscription.dto.CurrencyAmountResponse;

public record DashboardSummaryResponse(

        long activeSubscriptionCount,
        List<CurrencyAmountResponse> expectedAmounts,
        long upcomingPaymentCount

	) {
}