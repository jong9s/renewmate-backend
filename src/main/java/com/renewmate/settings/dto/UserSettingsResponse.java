package com.renewmate.settings.dto;

import com.renewmate.subscription.entity.Currency;

public record UserSettingsResponse(
        Currency defaultCurrency,
        Integer defaultReminderDays,
        Boolean emailNotificationEnabled
) {
}