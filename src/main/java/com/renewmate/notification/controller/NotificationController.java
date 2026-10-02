package com.renewmate.notification.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.renewmate.notification.dto.NotificationResponse;
import com.renewmate.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        
        notificationService.createUpcomingNotifications(userId);

        return ResponseEntity.ok(
                notificationService.getNotifications(userId)
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(Authentication authentication,
            @PathVariable("notificationId") Long notificationId
    ) {
        Long userId = (Long) authentication.getPrincipal();

        notificationService.markAsRead(
                userId,
                notificationId
        );

        return ResponseEntity.noContent().build();
    }
}