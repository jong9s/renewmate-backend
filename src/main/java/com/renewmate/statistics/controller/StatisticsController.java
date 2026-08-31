package com.renewmate.statistics.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.renewmate.statistics.dto.ServiceStatisticsResponse;
import com.renewmate.statistics.dto.StatisticsSummaryResponse;
import com.renewmate.statistics.service.StatisticsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/services")
    public ResponseEntity<List<ServiceStatisticsResponse>> getServiceStatistics(Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();

        List<ServiceStatisticsResponse> response = statisticsService.getServiceStatistics(userId);

        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/summary")
    public ResponseEntity<StatisticsSummaryResponse> getSummary(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        StatisticsSummaryResponse response = statisticsService.getSummary(userId);

        return ResponseEntity.ok(response);
    }
}