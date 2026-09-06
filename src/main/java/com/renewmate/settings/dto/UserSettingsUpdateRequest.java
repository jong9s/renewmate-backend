package com.renewmate.settings.dto;

import com.renewmate.subscription.entity.Currency;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UserSettingsUpdateRequest(

        @NotNull
        Currency defaultCurrency,

        @NotNull
        @PositiveOrZero
        @Max(30)
        Integer defaultReminderDays,

        @NotNull
        Boolean emailNotificationEnabled
) {
}