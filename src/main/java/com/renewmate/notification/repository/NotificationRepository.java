package com.renewmate.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.renewmate.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByUser_UserIdOrderByCreatedAtDesc(Long userId);

    Optional<Notification> findByNotificationIdAndUser_UserId(Long notificationId, Long userId);
    
    boolean existsByUser_UserIdAndSubscription_SubscriptionIdAndMessage(Long userId, Long subscriptionId, String message);
    
    void deleteAllByUser_UserIdAndSubscription_SubscriptionId(Long userId, Long subscriptionId);

    void deleteAllByUser_UserId(Long userId);
}
