package com.renewmate.auth.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.renewmate.auth.dto.PasswordResetRequest;
import com.renewmate.auth.service.PasswordResetService;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

/**
 * 메일은 커밋 후 비동기로 발송되므로 테스트 트랜잭션 없이 실제로 커밋하고 직접 정리한다.
 */
@SpringBootTest(properties = "app.password-reset.mail.retry-delay-ms=10")
@ActiveProfiles("test")
class PasswordResetMailIntegrationTest {

    private static final String EMAIL = "mail-retry@example.com";

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PasswordResetMailSender passwordResetMailSender;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.create("Mail Retry", EMAIL, "encoded-password")).getUserId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    @Test
    @DisplayName("일시적인 발송 오류는 재시도해 결국 메일을 보내고 토큰을 유지한다")
    void shouldRetryTransientFailureAndKeepToken() {
        doThrow(new MailSendException("smtp down"))
                .doThrow(new MailSendException("smtp down"))
                .doNothing()
                .when(passwordResetMailSender).send(anyString(), anyString(), anyString(), anyLong());

        passwordResetService.requestReset(new PasswordResetRequest(EMAIL));

        awaitTrue(() -> sendAttempts() == 3);
        assertEquals(1, tokenCount());
    }

    @Test
    @DisplayName("재시도까지 모두 실패하면 쓸 수 없는 토큰을 지운다")
    void shouldDiscardTokenWhenRetriesAreExhausted() {
        doThrow(new MailSendException("smtp down"))
                .when(passwordResetMailSender).send(anyString(), anyString(), anyString(), anyLong());

        passwordResetService.requestReset(new PasswordResetRequest(EMAIL));

        awaitTrue(() -> tokenCount() == 0);
        assertEquals(3, sendAttempts());
    }

    @Test
    @DisplayName("인증 실패처럼 다시 보내도 같은 오류는 재시도하지 않고 토큰을 지운다")
    void shouldNotRetryPermanentFailure() {
        doThrow(new MailAuthenticationException("bad credentials"))
                .when(passwordResetMailSender).send(anyString(), anyString(), anyString(), anyLong());

        passwordResetService.requestReset(new PasswordResetRequest(EMAIL));

        awaitTrue(() -> tokenCount() == 0);
        assertEquals(1, sendAttempts());
    }

    @Test
    @DisplayName("재설정 요청은 메일 발송을 기다리지 않고 바로 끝난다")
    void shouldNotWaitForSmtp() throws Exception {
        CountDownLatch smtpResponds = new CountDownLatch(1);
        doAnswer(invocation -> {
            smtpResponds.await(5, TimeUnit.SECONDS);
            return null;
        }).when(passwordResetMailSender).send(anyString(), anyString(), anyString(), anyLong());

        long started = System.nanoTime();
        passwordResetService.requestReset(new PasswordResetRequest(EMAIL));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertTrue(elapsed.toMillis() < 1_000, "요청이 SMTP 응답을 기다렸습니다: " + elapsed);
        assertEquals(1, tokenCount());

        smtpResponds.countDown();
        awaitTrue(() -> sendAttempts() == 1);
    }

    private long sendAttempts() {
        return mockingDetails(passwordResetMailSender).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("send"))
                .count();
    }

    private int tokenCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?", Integer.class, userId);
    }

    private void awaitTrue(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("5초 안에 조건이 충족되지 않았습니다");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
