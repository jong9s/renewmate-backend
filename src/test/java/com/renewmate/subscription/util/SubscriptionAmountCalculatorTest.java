package com.renewmate.subscription.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.renewmate.subscription.entity.BillingCycle;
import com.renewmate.subscription.entity.Currency;
import com.renewmate.subscription.entity.Subscription;

class SubscriptionAmountCalculatorTest {

    private final SubscriptionAmountCalculator calculator = new SubscriptionAmountCalculator();

    @Test
    @DisplayName("월간 구독은 결제 금액을 결제 간격으로 나눈 값이 월 환산 금액이다")
    void monthlyAmount() {
        Subscription subscription = subscription(new BigDecimal("17000"), BillingCycle.MONTHLY, 1);

        assertEquals(new BigDecimal("17000.00"), calculator.calculateMonthlyAmount(subscription));
    }

    @Test
    @DisplayName("연간 구독은 결제 금액을 12개월로 나눈 값이 월 환산 금액이다")
    void yearlyAmount() {
        Subscription subscription = subscription(new BigDecimal("120000"), BillingCycle.YEARLY, 1);

        assertEquals(new BigDecimal("10000.00"), calculator.calculateMonthlyAmount(subscription));
    }

    private Subscription subscription(BigDecimal amount, BillingCycle billingCycle, int billingInterval) {
        return Subscription.create(
                null,
                null,
                "Test Service",
                amount,
                Currency.KRW,
                billingCycle,
                billingInterval,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                true,
                3,
                null,
                null,
                null
        );
    }
}
