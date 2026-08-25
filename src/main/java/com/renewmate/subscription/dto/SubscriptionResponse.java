package com.renewmate.subscription.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.renewmate.subscription.entity.BillingCycle;
import com.renewmate.subscription.entity.Currency;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.entity.SubscriptionStatus;

public record SubscriptionResponse(
        Long subscriptionId,
        String serviceName,
        BigDecimal amount,
        Currency currency,
        BillingCycle billingCycle,
        Integer billingInterval,
        LocalDate startDate,
        LocalDate nextBillingDate,
        Boolean autoRenew,
        SubscriptionStatus status,
        Integer reminderDays,
        String paymentMethod,
        String serviceUrl,
        String memo
	) {
    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getSubscriptionId(),
                subscription.getServiceName(),
                subscription.getAmount(),
                subscription.getCurrency(),
                subscription.getBillingCycle(),
                subscription.getBillingInterval(),
                subscription.getStartDate(),
                subscription.getNextBillingDate(),
                subscription.getAutoRenew(),
                subscription.getStatus(),
                subscription.getReminderDays(),
                subscription.getPaymentMethod(),
                subscription.getServiceUrl(),
                subscription.getMemo()
        );
    }
}
