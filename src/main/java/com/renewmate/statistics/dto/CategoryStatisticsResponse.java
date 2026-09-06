package com.renewmate.statistics.dto;

import java.math.BigDecimal;

public record CategoryStatisticsResponse(
        Long categoryId,
        String categoryName,
        BigDecimal monthlyAmount,
        BigDecimal annualAmount
) {
}