package com.renewmate.auth.mail;

import org.springframework.mail.MailSendException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 재설정 메일 발송과 재시도.
 * 네트워크 단절·SMTP 일시 오류(MailSendException)만 재시도하고,
 * 인증 실패처럼 다시 보내도 결과가 같은 오류는 바로 실패시킨다.
 * 기본값: 최초 1회 + 재시도 2회, 간격 1초 → 2초.
 */
@Component
@RequiredArgsConstructor
public class PasswordResetMailDelivery {

    private final PasswordResetMailSender passwordResetMailSender;

    @Retryable(
            includes = MailSendException.class,
            maxRetriesString = "${app.password-reset.mail.max-retries:2}",
            delayString = "${app.password-reset.mail.retry-delay-ms:1000}",
            multiplier = 2
    )
    public void deliver(PasswordResetMailRequestedEvent event) {
        passwordResetMailSender.send(
                event.recipientEmail(),
                event.recipientName(),
                event.resetLink(),
                event.expirationMinutes()
        );
    }
}
