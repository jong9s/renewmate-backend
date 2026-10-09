package com.renewmate.statistics.dto;

import java.math.BigDecimal;

import com.renewmate.subscription.entity.Currency;

public record ServiceStatisticsResponse(
        String serviceName,
        Currency currency,
        BigDecimal monthlyAmount,
        BigDecimal annualAmount
	) {

}
