package com.renewmate.dashboard.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.renewmate.dashboard.dto.DashboardSummaryResponse;
import com.renewmate.dashboard.service.DashboardService;
import com.renewmate.subscription.dto.SubscriptionResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(Authentication authentication) {
    	
        Long userId = (Long) authentication.getPrincipal();

        DashboardSummaryResponse response = dashboardService.getSummary(userId);

        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/upcoming")
    public ResponseEntity<List<SubscriptionResponse>> getUpcoming(Authentication authentication,
            @RequestParam(name = "limit", defaultValue = "5") int limit
    ) {
        Long userId = (Long) authentication.getPrincipal();

        List<SubscriptionResponse> response =
                dashboardService.getUpcoming(userId, limit);

        return ResponseEntity.ok(response);
    }
}