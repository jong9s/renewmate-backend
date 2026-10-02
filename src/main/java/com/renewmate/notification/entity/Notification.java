package com.renewmate.notification.entity;

import java.time.LocalDateTime;

import com.renewmate.subscription.entity.Subscription;
import com.renewmate.user.entity.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(name = "is_read", nullable = false)
    private Boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Notification create(
            User user,
            Subscription subscription,
            String message
    ) {
        Notification notification = new Notification();

        notification.user = user;
        notification.subscription = subscription;
        notification.message = message;
        notification.read = false;
        notification.createdAt = LocalDateTime.now();

        return notification;
    }

    public void markAsRead() {
        this.read = true;
    }
}