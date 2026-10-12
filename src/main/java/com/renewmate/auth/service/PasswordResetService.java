package com.renewmate.auth.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.auth.dto.PasswordResetConfirmRequest;
import com.renewmate.auth.dto.PasswordResetRequest;
import com.renewmate.auth.entity.PasswordResetToken;
import com.renewmate.auth.mail.PasswordResetMailRequestedEvent;
import com.renewmate.auth.repository.PasswordResetTokenRepository;
import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.user.entity.User;
import com.renewmate.user.entity.UserStatus;
import com.renewmate.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${app.password-reset.expiration-minutes:30}")
    private long expirationMinutes;

    @Transactional
    public void requestReset(PasswordResetRequest request) {
        userRepository.findByEmailIgnoreCaseAndStatus(request.email().trim(), UserStatus.ACTIVE)
                .ifPresent(this::issueResetToken);
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHash(hashToken(request.token()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));

        LocalDateTime now = LocalDateTime.now();
        if (resetToken.isUsed() || resetToken.isExpired(now)
                || resetToken.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }

        resetToken.getUser().updatePassword(passwordEncoder.encode(request.newPassword()));
        resetToken.markUsed(now);
    }

    private void issueResetToken(User user) {
        passwordResetTokenRepository.deleteAllByUser_UserId(user.getUserId());

        String rawToken = createRawToken();
        PasswordResetToken resetToken = PasswordResetToken.create(
                user,
                hashToken(rawToken),
                LocalDateTime.now().plusMinutes(expirationMinutes)
        );
        passwordResetTokenRepository.save(resetToken);

        String resetLink = frontendBaseUrl + "/reset-password?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        // 메일은 커밋 후 별도 스레드에서 보낸다 (PasswordResetMailListener: 재시도·최종 실패 처리)
        eventPublisher.publishEvent(new PasswordResetMailRequestedEvent(
                resetToken.getPasswordResetTokenId(),
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                resetLink,
                expirationMinutes
        ));
    }

    private String createRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
