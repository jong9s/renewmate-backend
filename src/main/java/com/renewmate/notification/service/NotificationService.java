package com.renewmate.notification.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.notification.dto.NotificationResponse;
import com.renewmate.notification.entity.Notification;
import com.renewmate.notification.repository.NotificationRepository;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.entity.SubscriptionStatus;
import com.renewmate.subscription.repository.SubscriptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {

        return notificationRepository
                .findAllByUser_UserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void markAsRead(
            Long userId,
            Long notificationId
    ) {
        Notification notification = notificationRepository
                .findByNotificationIdAndUser_UserId(
                        notificationId,
                        userId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.NOTIFICATION_NOT_FOUND
                        )
                );

        notification.markAsRead();
    }
    
    @Transactional
    public void createUpcomingNotifications(Long userId) {

        List<Subscription> subscriptions = subscriptionRepository.findAllByUser_UserIdAndStatus(userId, SubscriptionStatus.ACTIVE);

        LocalDate today = LocalDate.now();

        for (Subscription subscription : subscriptions) {

            if (subscription.getReminderDays() == null) {
                continue;
            }
            
            if (subscription.getNextBillingDate() == null) {
                continue;
            }

            LocalDate reminderDate = subscription.getNextBillingDate().minusDays(subscription.getReminderDays());

            if (reminderDate.isAfter(today)) {
                continue;
            }

            String message =
                    subscription.getServiceName() + " 결제일이 " + subscription.getNextBillingDate() + " 예정되어 있습니다.";

            boolean alreadyExists = notificationRepository.existsByUser_UserIdAndSubscription_SubscriptionIdAndMessage(userId,
                                    subscription.getSubscriptionId(),
                                    message
                            );

            if (alreadyExists) {
                continue;
            }

            Notification notification = Notification.create(subscription.getUser(), subscription, message);

            notificationRepository.save(notification);
        }
    }
}