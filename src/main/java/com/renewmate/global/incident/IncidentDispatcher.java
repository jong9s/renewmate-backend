package com.renewmate.global.incident;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * GitHub repository_dispatch 이벤트(production-error)를 보낸다.
 * Slack 알림과 AI 수정은 GitHub Actions(incident-autofix.yml)가 담당하므로
 * 서버에는 Slack Webhook이나 AI API 키를 두지 않는다.
 */
@Component
public class IncidentDispatcher {

    private static final Logger log = LoggerFactory.getLogger(IncidentDispatcher.class);
    static final String EVENT_TYPE = "production-error";

    private final RestClient restClient = RestClient.create("https://api.github.com");

    @Value("${app.incident.github-repository:}")
    private String repository;

    @Value("${app.incident.github-token:}")
    private String token;

    @Async
    public void dispatch(IncidentReport report) {
        if (repository.isBlank() || token.isBlank()) {
            log.warn("Incident dispatch skipped: GitHub repository or token is not configured. fingerprint={}",
                    report.fingerprint());
            return;
        }

        try {
            restClient.post()
                    .uri("/repos/{repository}/dispatches", Map.of("repository", repository))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("event_type", EVENT_TYPE, "client_payload", report.toPayload()))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Incident dispatched to GitHub. fingerprint={}", report.fingerprint());
        } catch (RestClientException exception) {
            // 장애 보고 실패가 또 다른 장애 보고를 만들지 않도록 로그만 남긴다.
            log.warn("Incident dispatch failed. fingerprint={} reason={}",
                    report.fingerprint(), exception.getClass().getSimpleName());
        }
    }
}
