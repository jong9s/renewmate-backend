package com.renewmate.statistics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.statistics.dto.ServiceStatisticsResponse;
import com.renewmate.statistics.dto.StatisticsSummaryResponse;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.entity.SubscriptionStatus;
import com.renewmate.subscription.repository.SubscriptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public List<ServiceStatisticsResponse> getServiceStatistics(Long userId) {

        return subscriptionRepository
                .findAllByUser_UserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .stream()
                .map(subscription -> {

                    BigDecimal monthlyAmount = calculateMonthlyAmount(subscription);

                    BigDecimal annualAmount = monthlyAmount.multiply(BigDecimal.valueOf(12));

                    return new ServiceStatisticsResponse(
                            subscription.getServiceName(),
                            monthlyAmount,
                            annualAmount
                    );
                })
                .sorted((a, b) -> b.monthlyAmount().compareTo(a.monthlyAmount())).toList();
    }

    private BigDecimal calculateMonthlyAmount(Subscription subscription) {
        BigDecimal amount = subscription.getAmount();
        int interval = subscription.getBillingInterval();

        return switch (subscription.getBillingCycle()) {

            case WEEKLY ->
                    amount.multiply(BigDecimal.valueOf(52))
                            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

            case MONTHLY ->
                    amount.divide(BigDecimal.valueOf(interval), 2, RoundingMode.HALF_UP);

            case BIMONTHLY ->
                    amount.divide(BigDecimal.valueOf(2L * interval), 2, RoundingMode.HALF_UP);

            case QUARTERLY ->
                    amount.divide(BigDecimal.valueOf(3L * interval), 2, RoundingMode.HALF_UP);

            case SEMIANNUAL ->
                    amount.divide(BigDecimal.valueOf(6L * interval), 2, RoundingMode.HALF_UP);

            case YEARLY ->
                    amount.divide(BigDecimal.valueOf(12L * interval), 2, RoundingMode.HALF_UP);
        };
    }
    
    @Transactional(readOnly = true)
    public StatisticsSummaryResponse getSummary(Long userId) {

        List<Subscription> subscriptions =
                subscriptionRepository.findAllByUser_UserIdAndStatus(userId, SubscriptionStatus.ACTIVE);

        BigDecimal monthlyTotalAmount = subscriptions.stream()
                .map(this::calculateMonthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal annualTotalAmount =
                monthlyTotalAmount.multiply(BigDecimal.valueOf(12));

        BigDecimal averageMonthlyAmount;

        if (subscriptions.isEmpty()) {
            averageMonthlyAmount = BigDecimal.ZERO;
        } else {
            averageMonthlyAmount = monthlyTotalAmount.divide(
                    BigDecimal.valueOf(subscriptions.size()),
                    2,
                    RoundingMode.HALF_UP
            );
        }

        return new StatisticsSummaryResponse(
                subscriptions.size(),
                monthlyTotalAmount,
                annualTotalAmount,
                averageMonthlyAmount
        );
    }
}