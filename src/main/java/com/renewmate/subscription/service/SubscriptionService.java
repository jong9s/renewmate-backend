package com.renewmate.subscription.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.subscription.dto.SubscriptionCreateRequest;
import com.renewmate.subscription.dto.SubscriptionResponse;
import com.renewmate.subscription.dto.SubscriptionUpdateRequest;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.repository.SubscriptionRepository;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionService {
	
	private final SubscriptionRepository subscriptionRepository;
	private final UserRepository userRepository;
	
	@Transactional
	public void createSubscription(
			Long userId,
			SubscriptionCreateRequest request
	) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		
		LocalDate nextBillingDate = calculateNextBillingDate(
				request.startDate(),
				request.billingCycle(),
				request.billingInterval()
		);
		
		Subscription subscription = Subscription.create(
				user,
                request.serviceName(),
                request.amount(),
                request.currency(),
                request.billingCycle(),
                request.billingInterval(),
                request.startDate(),
                nextBillingDate,
                request.autoRenew(),
                request.reminderDays(),
                request.paymentMethod(),
                request.serviceUrl(),
                request.memo()
		);
		
		subscriptionRepository.save(subscription);
	}
	
	private LocalDate calculateNextBillingDate(
			LocalDate startDate,
			com.renewmate.subscription.entity.BillingCycle billingCycle,
			Integer billingInterval
	) {
        return switch (billingCycle) {
	        case WEEKLY ->
	                startDate.plusWeeks(billingInterval);
	
	        case MONTHLY ->
	                startDate.plusMonths(billingInterval);
	
	        case BIMONTHLY ->
	                startDate.plusMonths(2L * billingInterval);
	
	        case QUARTERLY ->
	                startDate.plusMonths(3L * billingInterval);
	
	        case SEMIANNUAL ->
	                startDate.plusMonths(6L * billingInterval);
	
	        case YEARLY ->
	                startDate.plusYears(billingInterval);
        };
	}
	
	@Transactional(readOnly = true)
	public List<SubscriptionResponse> getSubscription(Long userId){
		
		return subscriptionRepository.findAllByUser_UserId(userId)
				.stream() // 하나씩 처리
				.map(SubscriptionResponse::from) // 각각 다른 형태로 변환
				.toList(); // 변환한 걸 List 로 모음
	}
	
	@Transactional(readOnly = true)
	public SubscriptionResponse getSubscription(
	        Long userId,
	        Long subscriptionId
	) {
	    Subscription subscription =
	            subscriptionRepository
	                    .findBySubscriptionIdAndUser_UserId(
	                            subscriptionId,
	                            userId
	                    )
	                    .orElseThrow(() ->
	                            new BusinessException(
	                                    ErrorCode.SUBSCRIPTION_NOT_FOUND
	                            )
	                    );

	    return SubscriptionResponse.from(subscription);
	}
	
	@Transactional
	public void updateSubscription(
	        Long userId,
	        Long subscriptionId,
	        SubscriptionUpdateRequest request
	) {
	    Subscription subscription =
	            subscriptionRepository
	                    .findBySubscriptionIdAndUser_UserId(
	                            subscriptionId,
	                            userId
	                    )
	                    .orElseThrow(() ->
	                            new BusinessException(
	                                    ErrorCode.SUBSCRIPTION_NOT_FOUND
	                            )
	                    );

	    LocalDate nextBillingDate = calculateNextBillingDate(
	            request.startDate(),
	            request.billingCycle(),
	            request.billingInterval()
	    );

	    subscription.update(
	            request.serviceName(),
	            request.amount(),
	            request.currency(),
	            request.billingCycle(),
	            request.billingInterval(),
	            request.startDate(),
	            nextBillingDate,
	            request.autoRenew(),
	            request.reminderDays(),
	            request.paymentMethod(),
	            request.serviceUrl(),
	            request.memo()
	    );
	}
	
	@Transactional
	public void deleteSubscription(
	        Long userId,
	        Long subscriptionId
	) {
	    Subscription subscription =
	            subscriptionRepository
	                    .findBySubscriptionIdAndUser_UserId(
	                            subscriptionId,
	                            userId
	                    )
	                    .orElseThrow(() ->
	                            new BusinessException(
	                                    ErrorCode.SUBSCRIPTION_NOT_FOUND
	                            )
	                    );

	    subscriptionRepository.delete(subscription);
	}
}
