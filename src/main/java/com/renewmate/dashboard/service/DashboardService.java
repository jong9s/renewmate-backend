package com.renewmate.dashboard.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.dashboard.dto.DashboardSummaryResponse;
import com.renewmate.global.cache.CacheNames;
import com.renewmate.subscription.dto.SubscriptionResponse;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.entity.SubscriptionStatus;
import com.renewmate.subscription.repository.SubscriptionRepository;
import com.renewmate.subscription.util.SubscriptionAmountCalculator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAmountCalculator subscriptionAmountCalculator;

    @Cacheable(cacheNames = CacheNames.DASHBOARD_SUMMARY, key = "#userId")
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(Long userId) {

        List<Subscription> activeSubscriptions =
                subscriptionRepository.findAllByUser_UserIdAndStatus(
                        userId,
                        SubscriptionStatus.ACTIVE
                );

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysLater = today.plusDays(30);

        long upcomingPaymentCount =
                subscriptionRepository
                        .countByUser_UserIdAndStatusAndNextBillingDateBetween(
                                userId,
                                SubscriptionStatus.ACTIVE,
                                today,
                                thirtyDaysLater
                        );

        return new DashboardSummaryResponse(
                activeSubscriptions.size(),
                subscriptionAmountCalculator.summarizeByCurrency(activeSubscriptions),
                upcomingPaymentCount
        );
    }
    
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getUpcoming(
            Long userId,
            int limit
    ) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(30);

        return subscriptionRepository
                .findAllWithCategoryByUserIdAndStatusAndNextBillingDateBetween(userId, SubscriptionStatus.ACTIVE, today, endDate)
                .stream()
                .limit(limit)
                .map(SubscriptionResponse::from)
                .toList();
    }
}