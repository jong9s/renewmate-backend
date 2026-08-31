package com.renewmate.subscription.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.subscription.dto.SubscriptionCreateRequest;
import com.renewmate.subscription.dto.SubscriptionResponse;
import com.renewmate.subscription.dto.SubscriptionStatusUpdateRequest;
import com.renewmate.subscription.dto.SubscriptionUpdateRequest;
import com.renewmate.subscription.entity.BillingCycle;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.entity.SubscriptionStatus;
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
	public void createSubscription(Long userId,	SubscriptionCreateRequest request) {
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
	
	private LocalDate calculateMonthlyDate(LocalDate startDate, long months) {
	    LocalDate targetDate = startDate.plusMonths(months);

	    boolean startDateIsLastDay = startDate.getDayOfMonth() == startDate.lengthOfMonth();

	    if (startDateIsLastDay) {
	        return targetDate.withDayOfMonth(
	                targetDate.lengthOfMonth()
	        );
	    }

	    return targetDate;
	}
	
	private LocalDate calculateBillingDate(LocalDate startDate, BillingCycle billingCycle, Integer billingInterval,
	        int count
	) {
	    return switch (billingCycle) {

	        case WEEKLY ->
	                startDate.plusWeeks(
	                        (long) billingInterval * count
	                );

	        case MONTHLY ->
	                calculateMonthlyDate(
	                        startDate,
	                        (long) billingInterval * count
	                );

	        case BIMONTHLY ->
	                calculateMonthlyDate(
	                        startDate,
	                        2L * billingInterval * count
	                );

	        case QUARTERLY ->
	                calculateMonthlyDate(
	                        startDate,
	                        3L * billingInterval * count
	                );

	        case SEMIANNUAL ->
	                calculateMonthlyDate(
	                        startDate,
	                        6L * billingInterval * count
	                );

	        case YEARLY ->
	                startDate.plusYears(
	                        (long) billingInterval * count
	                );
	    };
	}
	
	private LocalDate calculateNextBillingDate(LocalDate startDate,	BillingCycle billingCycle, Integer billingInterval
	) {
		LocalDate today = LocalDate.now();
		
		int count = 1;
		
		LocalDate nextBillingDate = calculateBillingDate(startDate, billingCycle, billingInterval, count);
		
		while (!nextBillingDate.isAfter(today)) {
			count++;
			
			nextBillingDate = calculateBillingDate(startDate, billingCycle, billingInterval, count);
		}
		
		return nextBillingDate;
		
	}
	
	
	@Transactional(readOnly = true)
	public List<SubscriptionResponse> getSubscription(Long userId){
		
		return subscriptionRepository.findAllByUser_UserId(userId)
				.stream() // 하나씩 처리
				.map(SubscriptionResponse::from) // 각각 다른 형태로 변환
				.toList(); // 변환한 걸 List 로 모음
	}
	
	@Transactional(readOnly = true)
	public SubscriptionResponse getSubscription(Long userId, Long subscriptionId
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
	public void updateSubscription(Long userId, Long subscriptionId, SubscriptionUpdateRequest request
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
	public void deleteSubscription(Long userId, Long subscriptionId) {
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
	
	@Transactional
	public void changeStatus(Long userId, Long subscriptionId, SubscriptionStatusUpdateRequest request) {
			Subscription subscription = subscriptionRepository.findBySubscriptionIdAndUser_UserId(subscriptionId, userId)
					.orElseThrow(() -> new BusinessException(
								ErrorCode.SUBSCRIPTION_NOT_FOUND
						));
			subscription.changeStatus(request.status());
		
	}
	
	// 
	@Transactional
	public List<SubscriptionResponse> getUpcomingSubscriptions(Long userId, int days){
		LocalDate today = LocalDate.now();
		LocalDate endDate = today.plusDays(days);
		
		return subscriptionRepository.findAllByUser_UserIdAndStatusAndNextBillingDateBetween(userId, SubscriptionStatus.ACTIVE, today, endDate)
				.stream()
				.map(SubscriptionResponse::from)
				.toList();
	}
}
