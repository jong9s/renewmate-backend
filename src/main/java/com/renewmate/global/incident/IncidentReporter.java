package com.renewmate.global.incident;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

/**
 * 처리되지 않은 서버 오류를 GitHub Actions의 장애 대응 워크플로우로 전달한다.
 * 같은 지문의 오류는 쿨다운 동안 한 번만 전달해 Slack 알림과 AI 실행이 폭주하지 않게 한다.
 */
@Component
public class IncidentReporter {

    private static final int MAX_TRACKED_FINGERPRINTS = 1_000;

    private final IncidentDispatcher incidentDispatcher;
    private final Clock clock;
    private final Map<String, Instant> lastReportedAt = new ConcurrentHashMap<>();

    @Value("${app.incident.enabled:false}")
    private boolean enabled;

    @Value("${app.incident.cooldown-minutes:30}")
    private long cooldownMinutes;

    @Autowired
    public IncidentReporter(IncidentDispatcher incidentDispatcher) {
        this(incidentDispatcher, Clock.systemUTC());
    }

    IncidentReporter(IncidentDispatcher incidentDispatcher, Clock clock) {
        this.incidentDispatcher = incidentDispatcher;
        this.clock = clock;
    }

    public void report(Throwable exception, String method, String requestUri) {
        if (!enabled || isClientError(exception)) {
            return;
        }

        IncidentReport report = IncidentReport.from(exception, method, requestUri);
        if (isCoolingDown(report.fingerprint())) {
            return;
        }

        incidentDispatcher.dispatch(report);
    }

    /**
     * 없는 경로(404), 지원하지 않는 메서드(405), 잘못된 요청 본문·파라미터처럼
     * 코드 결함이 아닌 클라이언트 오류는 장애로 보고하지 않는다.
     */
    public static boolean isClientError(Throwable exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            return errorResponse.getStatusCode().is4xxClientError();
        }
        return exception instanceof HttpMessageNotReadableException
                || exception instanceof MethodArgumentTypeMismatchException
                || exception instanceof ConstraintViolationException;
    }

    private boolean isCoolingDown(String fingerprint) {
        Instant now = clock.instant();
        Duration cooldown = Duration.ofMinutes(cooldownMinutes);

        if (lastReportedAt.size() >= MAX_TRACKED_FINGERPRINTS) {
            lastReportedAt.values().removeIf(reportedAt -> reportedAt.plus(cooldown).isBefore(now));
        }

        boolean[] coolingDown = {false};
        lastReportedAt.compute(fingerprint, (key, reportedAt) -> {
            if (reportedAt != null && reportedAt.plus(cooldown).isAfter(now)) {
                coolingDown[0] = true;
                return reportedAt;
            }
            return now;
        });
        return coolingDown[0];
    }
}
