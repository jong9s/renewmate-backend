package com.renewmate.settings.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.renewmate.settings.dto.UserSettingsResponse;
import com.renewmate.settings.dto.UserSettingsUpdateRequest;
import com.renewmate.settings.service.UserSettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    @GetMapping
    public ResponseEntity<UserSettingsResponse> getSettings(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        UserSettingsResponse response = userSettingsService.getSettings(userId);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<Void> updateSettings(Authentication authentication,
    		@Valid @RequestBody UserSettingsUpdateRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();

        userSettingsService.updateSettings(userId, request);

        return ResponseEntity.noContent().build();
    }
}