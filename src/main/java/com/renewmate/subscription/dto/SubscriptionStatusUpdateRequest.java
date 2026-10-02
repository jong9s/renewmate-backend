package com.renewmate.subscription.dto;

import com.renewmate.subscription.entity.SubscriptionStatus;

import jakarta.validation.constraints.NotNull;

public record SubscriptionStatusUpdateRequest(
		
		@NotNull(message = "구독 상태는 필수입니다.")
		SubscriptionStatus status
	) {
	
}
