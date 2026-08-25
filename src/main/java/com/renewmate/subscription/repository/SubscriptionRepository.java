package com.renewmate.subscription.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.renewmate.subscription.entity.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long>{
	// SELECT *	FROM subscriptions WHERE user_id = ?;
	List<Subscription> findAllByUser_UserId(Long userID);
	
	// subscriptionId 가 일치하고 user.userId도 일치하는 구독 1건 조회
	Optional<Subscription> findBySubscriptionIdAndUser_UserId(
			Long subscriptionId,
			Long userId
		);
}
