package com.renewmate.subscription.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.renewmate.subscription.entity.BillingCycle;
import com.renewmate.subscription.entity.Currency;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SubscriptionUpdateRequest(

        @NotBlank(message = "서비스명은 필수입니다.")
        String serviceName,

        @NotNull(message = "금액은 필수입니다.")
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal amount,

        @NotNull(message = "통화는 필수입니다.")
        Currency currency,

        @NotNull(message = "결제 주기는 필수입니다.")
        BillingCycle billingCycle,

        @NotNull(message = "결제 간격은 필수입니다.")
        @Positive
        Integer billingInterval,

        @NotNull(message = "시작일은 필수입니다.")
        LocalDate startDate,

        @NotNull(message = "자동 갱신 여부는 필수입니다.")
        Boolean autoRenew,

        Integer reminderDays,
        
        String paymentMethod,
        
        String serviceUrl,
        
        String memo

	) {
}