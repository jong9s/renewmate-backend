package com.renewmate.settings.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.settings.dto.UserSettingsResponse;
import com.renewmate.settings.dto.UserSettingsUpdateRequest;
import com.renewmate.settings.entity.UserSettings;
import com.renewmate.settings.repository.UserSettingsRepository;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserSettingsResponse getSettings(Long userId) {

        UserSettings settings = userSettingsRepository
                .findByUser_UserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));

        return new UserSettingsResponse(
                settings.getDefaultCurrency(),
                settings.getDefaultReminderDays(),
                settings.getEmailNotificationEnabled()
        );
    }

    @Transactional
    public void updateSettings(
            Long userId,
            UserSettingsUpdateRequest request
    ) {
        UserSettings settings = userSettingsRepository
                .findByUser_UserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));

        settings.update(
                request.defaultCurrency(),
                request.defaultReminderDays(),
                request.emailNotificationEnabled()
        );
    }

    private UserSettings createDefaultSettings(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        UserSettings settings = UserSettings.create(user);

        return userSettingsRepository.save(settings);
    }
}