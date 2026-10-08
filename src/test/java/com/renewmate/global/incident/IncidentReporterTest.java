package com.renewmate.global.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ExtendWith(MockitoExtension.class)
class IncidentReporterTest {

    @Mock
    private IncidentDispatcher incidentDispatcher;

    private MutableClock clock;
    private IncidentReporter incidentReporter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-08T00:00:00Z"));
        incidentReporter = new IncidentReporter(incidentDispatcher, clock);
        ReflectionTestUtils.setField(incidentReporter, "enabled", true);
        ReflectionTestUtils.setField(incidentReporter, "cooldownMinutes", 30L);
    }

    @Test
    @DisplayName("같은 지문의 장애는 쿨다운 동안 한 번만 전달한다")
    void reportsSameFingerprintOncePerCooldown() {
        incidentReporter.report(failure(), "GET", "/api/statistics/summary");
        incidentReporter.report(failure(), "GET", "/api/statistics/summary");

        verify(incidentDispatcher, times(1)).dispatch(any());

        clock.advance(Duration.ofMinutes(31));
        incidentReporter.report(failure(), "GET", "/api/statistics/summary");

        verify(incidentDispatcher, times(2)).dispatch(any());
    }

    @Test
    @DisplayName("비활성화 상태에서는 장애를 전달하지 않는다")
    void skipsWhenDisabled() {
        ReflectionTestUtils.setField(incidentReporter, "enabled", false);

        incidentReporter.report(failure(), "GET", "/api/statistics/summary");

        verify(incidentDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("없는 경로 요청 같은 클라이언트 오류는 장애로 보고하지 않는다")
    void skipsClientErrors() {
        NoResourceFoundException notFound = new NoResourceFoundException(HttpMethod.GET, "/wp-admin", "wp-admin");

        incidentReporter.report(notFound, "GET", "/wp-admin");

        assertTrue(IncidentReporter.isClientError(notFound));
        assertFalse(IncidentReporter.isClientError(failure()));
        verify(incidentDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("예외 메시지의 이메일과 비밀값은 마스킹하고 경로의 ID는 정규화한다")
    void sanitizesReport() {
        IllegalStateException exception = new IllegalStateException(
                "user test@example.com failed, password=hunter2 token abcdefghijklmnopqrstuvwxyz0123456789");

        IncidentReport report = IncidentReport.from(exception, "PATCH", "/api/subscriptions/42/status");

        assertEquals("user <email> failed, password=<masked> token <masked>", report.message());
        assertEquals("/api/subscriptions/{id}/status", report.path());
        assertEquals(12, report.fingerprint().length());
        assertTrue(report.location().startsWith("com.renewmate."));
    }

    @Test
    @DisplayName("원인 예외 종류가 다르면 지문이 달라진다")
    void fingerprintDependsOnRootCause() {
        IncidentReport first = IncidentReport.from(failure(), "GET", "/a");
        IncidentReport second = IncidentReport.from(new RuntimeException(new ArithmeticException()), "GET", "/a");

        assertNotEquals(first.fingerprint(), second.fingerprint());
    }

    private IllegalStateException failure() {
        return new IllegalStateException("boom");
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
