package com.renewmate.notification.dto;

import java.time.LocalDateTime;

import com.renewmate.notification.entity.Notification;

public record NotificationResponse(
        Long notificationId,
        Long subscriptionId,
        String serviceName,
        String message,
        Boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getSubscription().getSubscriptionId(),
                notification.getSubscription().getServiceName(),
                notification.getMessage(),
                notification.getRead(),
                notification.getCreatedAt()
        );
    }
}