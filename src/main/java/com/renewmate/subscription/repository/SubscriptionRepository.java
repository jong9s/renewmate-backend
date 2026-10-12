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
	
	// 결제 예정 목록 응답에 카테고리 이름이 필요하므로 함께 조회 (카테고리가 없는 구독도 포함)
	@Query("""
			select subscription
			from Subscription subscription
			left join fetch subscription.category
			where subscription.user.userId = :userId
			and subscription.status = :status
			and subscription.nextBillingDate between :startDate and :endDate
			order by subscription.nextBillingDate asc
			""")
	List<Subscription> findAllWithCategoryByUserIdAndStatusAndNextBillingDateBetween(
			@Param("userId") Long userId,
			@Param("status") SubscriptionStatus status,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate
	);

	long countByUser_UserIdAndStatusAndNextBillingDateBetween(
			Long userId,
			SubscriptionStatus status,
			LocalDate startDate,
			LocalDate endDate
	);

	// 카테고리별 통계용: 카테고리를 구독마다 따로 조회하지 않도록 함께 조회
	@Query("""
			select subscription
			from Subscription subscription
			left join fetch subscription.category
			where subscription.user.userId = :userId
			and subscription.status = :status
			""")
	List<Subscription> findAllWithCategoryByUserIdAndStatus(
			@Param("userId") Long userId,
			@Param("status") SubscriptionStatus status
	);

	List<Subscription> findAllByUser_UserIdAndStatus(
	        Long userId,
	        SubscriptionStatus status
	);

	void deleteAllByUser_UserId(Long userId);
}
