package com.renewmate.statistics.dto;

import java.math.BigDecimal;

import com.renewmate.subscription.entity.Currency;

// 같은 카테고리라도 통화가 다르면 별도 항목으로 반환한다
public record CategoryStatisticsResponse(
        Long categoryId,
        String categoryName,
        Currency currency,
        BigDecimal monthlyAmount,
        BigDecimal annualAmount
) {
}