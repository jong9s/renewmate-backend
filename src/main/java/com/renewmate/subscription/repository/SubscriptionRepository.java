package com.renewmate.subscription.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.entity.SubscriptionStatus;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long>{
	// SELECT *	FROM subscriptions WHERE user_id = ?;
	List<Subscription> findAllByUser_UserId(Long userID);

	@Query("""
			select subscription
			from Subscription subscription
			join fetch subscription.category
			where subscription.user.userId = :userId
			""")
	List<Subscription> findAllWithCategoryByUserId(@Param("userId") Long userId);
	
	// subscriptionId 가 일치하고 user.userId도 일치하는 구독 1건 조회
	Optional<Subscription> findBySubscriptionIdAndUser_UserId(
			Long subscriptionId,
			Long userId
	);
	
	List<Subscription> findAllByUser_UserIdAndStatusAndNextBillingDateBetween(
            Long userId,
            SubscriptionStatus status,
            LocalDate startDate,
            LocalDate endDate
    );
	
	List<Subscription> findAllByUser_UserIdAndStatus(
	        Long userId,
	        SubscriptionStatus status
	);

	void deleteAllByUser_UserId(Long userId);
}
