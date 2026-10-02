package com.renewmate.settings.entity;

import java.time.LocalDateTime;

import com.renewmate.subscription.entity.Currency;
import com.renewmate.user.entity.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "setting_id")
    private Long settingId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_currency", nullable = false)
    private Currency defaultCurrency;

    @Column(name = "default_reminder_days", nullable = false)
    private Integer defaultReminderDays;

    @Column(name = "email_notification_enabled", nullable = false)
    private Boolean emailNotificationEnabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static UserSettings create(User user) {
        UserSettings settings = new UserSettings();

        settings.user = user;
        settings.defaultCurrency = Currency.KRW;
        settings.defaultReminderDays = 3;
        settings.emailNotificationEnabled = true;
        settings.createdAt = LocalDateTime.now();
        settings.updatedAt = LocalDateTime.now();

        return settings;
    }

    public void update(
            Currency defaultCurrency,
            Integer defaultReminderDays,
            Boolean emailNotificationEnabled
    ) {
        this.defaultCurrency = defaultCurrency;
        this.defaultReminderDays = defaultReminderDays;
        this.emailNotificationEnabled = emailNotificationEnabled;
        this.updatedAt = LocalDateTime.now();
    }
}