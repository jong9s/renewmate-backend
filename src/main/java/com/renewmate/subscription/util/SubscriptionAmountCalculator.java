package com.renewmate.subscription.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.renewmate.subscription.entity.Subscription;

@Component
public class SubscriptionAmountCalculator {

    public BigDecimal calculateMonthlyAmount(Subscription subscription) {

        BigDecimal amount = subscription.getAmount();
        int interval = subscription.getBillingInterval();

        return switch (subscription.getBillingCycle()) {

            case WEEKLY ->
                    amount.multiply(BigDecimal.valueOf(52))
                            .divide(
                                    BigDecimal.valueOf(12),
                                    2,
                                    RoundingMode.HALF_UP
                            );

            case MONTHLY ->
                    amount.divide(
                            BigDecimal.valueOf(interval),
                            2,
                            RoundingMode.HALF_UP
                    );

            case BIMONTHLY ->
                    amount.divide(
                            BigDecimal.valueOf(2L * interval),
                            2,
                            RoundingMode.HALF_UP
                    );

            case QUARTERLY ->
                    amount.divide(
                            BigDecimal.valueOf(3L * interval),
                            2,
                            RoundingMode.HALF_UP
                    );

            case SEMIANNUAL ->
                    amount.divide(
                            BigDecimal.valueOf(6L * interval),
                            2,
                            RoundingMode.HALF_UP
                    );

            case YEARLY ->
                    amount.divide(
                            BigDecimal.valueOf(12L * interval),
                            2,
                            RoundingMode.HALF_UP
                    );
        };
    }
}