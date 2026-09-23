package com.renewmate.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.auth.repository.PasswordResetTokenRepository;
import com.renewmate.auth.repository.OAuthExchangeCodeRepository;
import com.renewmate.notification.repository.NotificationRepository;
import com.renewmate.settings.repository.UserSettingsRepository;
import com.renewmate.subscription.repository.SubscriptionRepository;
import com.renewmate.user.dto.UserWithdrawalRequest;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private OAuthExchangeCodeRepository oauthExchangeCodeRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("회원 탈퇴 시 연관 데이터를 외래 키 순서에 맞게 삭제한다")
    void shouldDeleteUserAndRelatedDataInOrder() {
        Long userId = 1L;
        User user = org.mockito.Mockito.mock(User.class);
        UserWithdrawalRequest request = new UserWithdrawalRequest("password123");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(user.getPassword()).thenReturn("encoded-password");
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        userService.withdraw(userId, request);

        InOrder inOrder = inOrder(
                passwordResetTokenRepository,
                oauthExchangeCodeRepository,
                notificationRepository,
                subscriptionRepository,
                userSettingsRepository,
                userRepository
        );
        inOrder.verify(passwordResetTokenRepository).deleteAllByUser_UserId(userId);
        inOrder.verify(oauthExchangeCodeRepository).deleteAllByUser_UserId(userId);
        inOrder.verify(notificationRepository).deleteAllByUser_UserId(userId);
        inOrder.verify(subscriptionRepository).deleteAllByUser_UserId(userId);
        inOrder.verify(userSettingsRepository).deleteByUser_UserId(userId);
        inOrder.verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("현재 비밀번호가 다르면 회원 탈퇴를 거부한다")
    void shouldRejectWithdrawalWhenPasswordIsInvalid() {
        Long userId = 1L;
        User user = org.mockito.Mockito.mock(User.class);
        UserWithdrawalRequest request = new UserWithdrawalRequest("wrong-password");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(user.getPassword()).thenReturn("encoded-password");
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.withdraw(userId, request)
        );

        assertEquals(ErrorCode.INVALID_CURRENT_PASSWORD, exception.getErrorCode());
        verify(passwordResetTokenRepository, never()).deleteAllByUser_UserId(userId);
        verify(oauthExchangeCodeRepository, never()).deleteAllByUser_UserId(userId);
        verify(notificationRepository, never()).deleteAllByUser_UserId(userId);
        verify(subscriptionRepository, never()).deleteAllByUser_UserId(userId);
        verify(userSettingsRepository, never()).deleteByUser_UserId(userId);
        verify(userRepository, never()).delete(user);
    }
}
