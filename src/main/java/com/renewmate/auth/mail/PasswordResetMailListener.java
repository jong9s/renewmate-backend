package com.renewmate.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.renewmate.auth.repository.PasswordResetTokenRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 재설정 요청 트랜잭션이 커밋된 뒤 별도 스레드에서 메일을 보낸다.
 * - 요청 응답이 SMTP를 기다리지 않는다.
 * - 메일 발송 여부와 관계없이 응답 시간이 같아져, 응답 시간으로 가입 여부를 추측할 수 없다.
 * - 커밋 후에 보내므로 저장되지 않은 토큰의 링크가 나가는 일이 없다.
 * 재시도까지 실패하면 쓸 수 없는 토큰을 지우고 기록을 남긴다. 사용자는 재설정을 다시 요청하면 된다.
 */
@Component
public class PasswordResetMailListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailListener.class);

    private final PasswordResetMailDelivery passwordResetMailDelivery;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final Counter failureCounter;

    public PasswordResetMailListener(
            PasswordResetMailDelivery passwordResetMailDelivery,
            PasswordResetTokenRepository passwordResetTokenRepository,
            MeterRegistry meterRegistry
    ) {
        this.passwordResetMailDelivery = passwordResetMailDelivery;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.failureCounter = Counter.builder("renewmate.password_reset.mail.failures")
                .description("재시도 후에도 보내지 못한 비밀번호 재설정 메일 수")
                .register(meterRegistry);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetMailRequested(PasswordResetMailRequestedEvent event) {
        try {
            passwordResetMailDelivery.deliver(event);
        } catch (RuntimeException exception) {
            passwordResetTokenRepository.deleteById(event.tokenId());
            failureCounter.increment();
            log.error("Password reset email failed after retries; token discarded. userId={}",
                    event.userId(), exception);
        }
    }
}
