package com.renewmate.dashboard.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.dashboard.dto.DashboardSummaryResponse;
import com.renewmate.subscription.dto.SubscriptionResponse;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.entity.SubscriptionStatus;
import com.renewmate.subscription.repository.SubscriptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(Long userId) {

        List<Subscription> activeSubscriptions =
                subscriptionRepository.findAllByUser_UserIdAndStatus(
                        userId,
                        SubscriptionStatus.ACTIVE
                );

        BigDecimal monthlyExpectedAmount = activeSubscriptions.stream()
                .map(this::calculateMonthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal annualExpectedAmount = monthlyExpectedAmount
                .multiply(BigDecimal.valueOf(12));

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysLater = today.plusDays(30);

        long upcomingPaymentCount =
                subscriptionRepository
                        .findAllByUser_UserIdAndStatusAndNextBillingDateBetween(
                                userId,
                                SubscriptionStatus.ACTIVE,
                                today,
                                thirtyDaysLater
                        )
                        .size();

        return new DashboardSummaryResponse(
                activeSubscriptions.size(),
                monthlyExpectedAmount,
                annualExpectedAmount,
                upcomingPaymentCount
        );
    }

    private BigDecimal calculateMonthlyAmount(Subscription subscription) {

        BigDecimal amount = subscription.getAmount();
        int interval = subscription.getBillingInterval();

        return switch (subscription.getBillingCycle()) {

            case WEEKLY ->
                    amount
                            .multiply(BigDecimal.valueOf(52))
                            .divide(BigDecimal.valueOf(12));

            case MONTHLY ->
                    amount.divide(BigDecimal.valueOf(interval));

            case BIMONTHLY ->
                    amount.divide(
                            BigDecimal.valueOf(2L * interval)
                    );

            case QUARTERLY ->
                    amount.divide(
                            BigDecimal.valueOf(3L * interval)
                    );

            case SEMIANNUAL ->
                    amount.divide(
                            BigDecimal.valueOf(6L * interval)
                    );

            case YEARLY ->
                    amount.divide(
                            BigDecimal.valueOf(12L * interval)
            );
        };
    }
    
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getUpcoming(
            Long userId,
            int limit
    ) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(30);

        return subscriptionRepository
                .findAllByUser_UserIdAndStatusAndNextBillingDateBetween(userId, SubscriptionStatus.ACTIVE, today, endDate)
                .stream()
                .sorted((a, b) -> a.getNextBillingDate().compareTo(b.getNextBillingDate()))
                .limit(limit)
                .map(SubscriptionResponse::from)
                .toList();
    }
}