package com.renewmate.subscription.dto;

import java.math.BigDecimal;

import com.renewmate.subscription.entity.Currency;

/**
 * 한 통화의 구독 금액 합계. 통화가 다른 금액은 환산하지 않고 통화별로 따로 합산한다.
 */
public record CurrencyAmountResponse(
        Currency currency,
        long subscriptionCount,
        BigDecimal monthlyAmount,
        BigDecimal annualAmount,
        BigDecimal averageMonthlyAmount
) {
}
