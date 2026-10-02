package com.renewmate.statistics.dto;

import java.math.BigDecimal;

public record ServiceStatisticsResponse(
        String serviceName,
        BigDecimal monthlyAmount,
        BigDecimal annualAmount
	) {
	
}
