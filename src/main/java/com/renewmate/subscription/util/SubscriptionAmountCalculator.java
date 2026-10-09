package com.renewmate.subscription.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.renewmate.subscription.dto.CurrencyAmountResponse;
import com.renewmate.subscription.entity.Currency;
import com.renewmate.subscription.entity.Subscription;

@Component
public class SubscriptionAmountCalculator {

    /**
     * 구독을 통화별로 묶어 월·연 환산 금액을 합산한다. 결과는 Currency 선언 순서(KRW, USD, ...)로 정렬된다.
     */
    public List<CurrencyAmountResponse> summarizeByCurrency(Collection<Subscription> subscriptions) {

        Map<Currency, List<Subscription>> byCurrency = subscriptions.stream()
                .collect(Collectors.groupingBy(
                        Subscription::getCurrency,
                        () -> new EnumMap<>(Currency.class),
                        Collectors.toList()
                ));

        return byCurrency.entrySet()
                .stream()
                .map(entry -> {

                    List<Subscription> group = entry.getValue();

                    BigDecimal monthlyAmount = group.stream()
                            .map(this::calculateMonthlyAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new CurrencyAmountResponse(
                            entry.getKey(),
                            group.size(),
                            monthlyAmount,
                            monthlyAmount.multiply(BigDecimal.valueOf(12)),
                            monthlyAmount.divide(
                                    BigDecimal.valueOf(group.size()),
                                    2,
                                    RoundingMode.HALF_UP
                            )
                    );
                })
                .toList();
    }

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