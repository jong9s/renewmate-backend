package com.renewmate.statistics.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.category.entity.Category;
import com.renewmate.global.cache.CacheNames;
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

    @Cacheable(cacheNames = CacheNames.STATISTICS_SERVICES, key = "#userId")
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
                            subscription.getCurrency(),
                            monthlyAmount,
                            annualAmount
                    );
                })
                // 통화가 다른 금액은 비교할 수 없으므로 통화별로 묶은 뒤 금액 순으로 정렬
                .sorted(Comparator.comparing(ServiceStatisticsResponse::currency)
                        .thenComparing(ServiceStatisticsResponse::monthlyAmount, Comparator.reverseOrder()))
                .toList();
    }
    
    @Cacheable(cacheNames = CacheNames.STATISTICS_SUMMARY, key = "#userId")
    @Transactional(readOnly = true)
    public StatisticsSummaryResponse getSummary(Long userId) {

        List<Subscription> subscriptions =
                subscriptionRepository.findAllByUser_UserIdAndStatus(userId, SubscriptionStatus.ACTIVE);

        return new StatisticsSummaryResponse(
                subscriptions.size(),
                subscriptionAmountCalculator.summarizeByCurrency(subscriptions)
        );
    }
    
    @Cacheable(cacheNames = CacheNames.STATISTICS_CATEGORIES, key = "#userId")
    @Transactional(readOnly = true)
    public List<CategoryStatisticsResponse> getCategoryStatistics(Long userId) {

        List<Subscription> subscriptions =
                subscriptionRepository.findAllWithCategoryByUserIdAndStatus(
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
                .flatMap(entry -> {

                    Category category = entry.getKey();

                    // 같은 카테고리 안에서도 통화별로 따로 합산
                    return subscriptionAmountCalculator.summarizeByCurrency(entry.getValue())
                            .stream()
                            .map(total -> new CategoryStatisticsResponse(
                                    category.getCategoryId(),
                                    category.getName(),
                                    total.currency(),
                                    total.monthlyAmount(),
                                    total.annualAmount()
                            ));
                })
                .sorted(Comparator.comparing(CategoryStatisticsResponse::currency)
                        .thenComparing(CategoryStatisticsResponse::monthlyAmount, Comparator.reverseOrder()))
                .toList();
    }
}