package com.renewmate.dashboard.dto;

import java.math.BigDecimal;

public record DashboardSummaryResponse(
		
        long activeSubscriptionCount,
        BigDecimal monthlyExpectedAmount,
        BigDecimal annualExpectedAmount,
        long upcomingPaymentCount
        
	) {
}