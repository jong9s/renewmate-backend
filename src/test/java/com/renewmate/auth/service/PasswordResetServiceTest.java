package com.renewmate.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "frontendBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(passwordResetService, "expirationMinutes", 30L);
    }

    @Test
    @DisplayName("등록된 이메일에는 원문을 저장하지 않은 일회용 재설정 토큰을 발급하고 메일 발송 이벤트를 발행한다")
    void shouldIssueHashedResetTokenAndSendEmail() {
        PasswordResetRequest request = new PasswordResetRequest("user@example.com");
        User user = org.mockito.Mockito.mock(User.class);

        when(userRepository.findByEmailIgnoreCaseAndStatus("user@example.com", UserStatus.ACTIVE))
                .thenReturn(Optional.of(user));
        when(user.getUserId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getName()).thenReturn("사용자");

        passwordResetService.requestReset(request);

        verify(passwordResetTokenRepository).deleteAllByUser_UserId(1L);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        ArgumentCaptor<PasswordResetMailRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(PasswordResetMailRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PasswordResetMailRequestedEvent event = eventCaptor.getValue();
        assertEquals("user@example.com", event.recipientEmail());
        assertEquals("사용자", event.recipientName());
        assertEquals(30L, event.expirationMinutes());
        assertTrue(event.resetLink().startsWith("http://localhost:5173/reset-password?token="));
        // 원문 토큰이 든 링크는 toString()(로그)에 나오지 않는다
        assertFalse(event.toString().contains("token="));
    }

    @Test
    @DisplayName("존재하지 않는 이메일도 오류를 노출하지 않는다")
    void shouldNotRevealUnknownEmail() {
        PasswordResetRequest request = new PasswordResetRequest("unknown@example.com");
        when(userRepository.findByEmailIgnoreCaseAndStatus("unknown@example.com", UserStatus.ACTIVE))
                .thenReturn(Optional.empty());

        passwordResetService.requestReset(request);

        verify(passwordResetTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("유효한 토큰으로 비밀번호를 재설정하고 토큰을 사용 처리한다")
    void shouldResetPasswordAndConsumeToken() throws Exception {
        String rawToken = "raw-reset-token";
        User user = org.mockito.Mockito.mock(User.class);
        PasswordResetToken resetToken = PasswordResetToken.create(
                user,
                hash(rawToken),
                LocalDateTime.now().plusMinutes(10)
        );
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                rawToken,
                "new-password",
                "new-password"
        );

        when(passwordResetTokenRepository.findByTokenHash(hash(rawToken)))
                .thenReturn(Optional.of(resetToken));
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");

        passwordResetService.confirmReset(request);

        verify(user).updatePassword("encoded-new-password");
        assertNotNull(resetToken.getUsedAt());
    }

    @Test
    @DisplayName("이미 사용한 재설정 토큰은 다시 사용할 수 없다")
    void shouldRejectReusedToken() throws Exception {
        String rawToken = "used-reset-token";
        User user = org.mockito.Mockito.mock(User.class);
        PasswordResetToken resetToken = PasswordResetToken.create(
                user,
                hash(rawToken),
                LocalDateTime.now().plusMinutes(10)
        );
        resetToken.markUsed(LocalDateTime.now());

        when(passwordResetTokenRepository.findByTokenHash(hash(rawToken)))
                .thenReturn(Optional.of(resetToken));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> passwordResetService.confirmReset(new PasswordResetConfirmRequest(
                        rawToken,
                        "new-password",
                        "new-password"
                ))
        );

        assertEquals(ErrorCode.INVALID_PASSWORD_RESET_TOKEN, exception.getErrorCode());
        verify(user, never()).updatePassword(any());
    }

    @Test
    @DisplayName("만료된 재설정 토큰은 사용할 수 없다")
    void shouldRejectExpiredToken() throws Exception {
        String rawToken = "expired-reset-token";
        User user = org.mockito.Mockito.mock(User.class);
        PasswordResetToken resetToken = PasswordResetToken.create(
                user,
                hash(rawToken),
                LocalDateTime.now().minusSeconds(1)
        );

        when(passwordResetTokenRepository.findByTokenHash(hash(rawToken)))
                .thenReturn(Optional.of(resetToken));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> passwordResetService.confirmReset(new PasswordResetConfirmRequest(
                        rawToken,
                        "new-password",
                        "new-password"
                ))
        );

        assertEquals(ErrorCode.INVALID_PASSWORD_RESET_TOKEN, exception.getErrorCode());
        verify(user, never()).updatePassword(any());
    }

    private String hash(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
