package com.renewmate.statistics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.category.entity.Category;
import com.renewmate.statistics.dto.CategoryStatisticsResponse;
import com.renewmate.statistics.dto.ServiceStatisticsResponse;
import com.renewmate.statistics.dto.StatisticsSummaryResponse;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.entity.SubscriptionStatus;
import com.renewmate.subscription.repository.SubscriptionRepository;
import com.renewmate.subscription.util.SubscriptionAmountCalculator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAmountCalculator subscriptionAmountCalculator;

    @Transactional(readOnly = true)
    public List<ServiceStatisticsResponse> getServiceStatistics(Long userId) {

        return subscriptionRepository
                .findAllByUser_UserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .stream()
                .map(subscription -> {

                    BigDecimal monthlyAmount = subscriptionAmountCalculator.calculateMonthlyAmount(subscription);

                    BigDecimal annualAmount = monthlyAmount.multiply(BigDecimal.valueOf(12));

                    return new ServiceStatisticsResponse(
                            subscription.getServiceName(),
                            monthlyAmount,
                            annualAmount
                    );
                })
                .sorted((a, b) -> b.monthlyAmount().compareTo(a.monthlyAmount())).toList();
    }
    
    @Transactional(readOnly = true)
    public StatisticsSummaryResponse getSummary(Long userId) {

        List<Subscription> subscriptions =
                subscriptionRepository.findAllByUser_UserIdAndStatus(userId, SubscriptionStatus.ACTIVE);

        BigDecimal monthlyTotalAmount = subscriptions.stream()
                .map(subscriptionAmountCalculator::calculateMonthlyAmount)
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
    
    @Transactional(readOnly = true)
    public List<CategoryStatisticsResponse> getCategoryStatistics(Long userId) {

        List<Subscription> subscriptions =
                subscriptionRepository.findAllByUser_UserIdAndStatus(
                        userId,
                        SubscriptionStatus.ACTIVE
                );

        return subscriptions.stream()
                .filter(subscription -> subscription.getCategory() != null)
                .collect(Collectors.groupingBy(
                        Subscription::getCategory
                ))
                .entrySet()
                .stream()
                .map(entry -> {

                    Category category = entry.getKey();

                    BigDecimal monthlyAmount = entry.getValue()
                            .stream()
                            .map(subscriptionAmountCalculator::calculateMonthlyAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal annualAmount = monthlyAmount.multiply(BigDecimal.valueOf(12));

                    return new CategoryStatisticsResponse(
                            category.getCategoryId(),
                            category.getName(),
                            monthlyAmount,
                            annualAmount
                    );
                })
                .sorted((a, b) ->
                        b.monthlyAmount()
                                .compareTo(a.monthlyAmount())
                )
                .toList();
    }
}