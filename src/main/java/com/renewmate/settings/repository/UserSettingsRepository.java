package com.renewmate.settings.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.renewmate.settings.entity.UserSettings;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByUser_UserId(Long userId);

    void deleteByUser_UserId(Long userId);
}
