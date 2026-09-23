package com.renewmate.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import com.renewmate.auth.dto.LoginResponse;
import com.renewmate.auth.entity.OAuthExchangeCode;
import com.renewmate.auth.repository.OAuthExchangeCodeRepository;
import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.user.entity.User;
import com.renewmate.user.entity.UserStatus;

@ExtendWith(MockitoExtension.class)
class OAuthLoginServiceTest {

    @Mock
    private OAuthExchangeCodeRepository exchangeCodeRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private OAuthLoginService oauthLoginService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(oauthLoginService, "expirationSeconds", 60L);
    }

    @Test
    @DisplayName("Google 인증 성공 시 원문을 노출하지 않는 일회용 교환 코드를 발급한다")
    void shouldIssueHashedExchangeCode() {
        OidcUser oidcUser = org.mockito.Mockito.mock(OidcUser.class);
        User user = org.mockito.Mockito.mock(User.class);
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn("USER@EXAMPLE.COM");
        when(oidcUser.getFullName()).thenReturn("사용자");
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(authService.resolveGoogleUser(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(user.getUserId()).thenReturn(1L);

        String rawCode = oauthLoginService.issueExchangeCode(oidcUser);

        ArgumentCaptor<OAuthExchangeCode> captor = ArgumentCaptor.forClass(OAuthExchangeCode.class);
        verify(exchangeCodeRepository).deleteAllByUser_UserId(1L);
        verify(exchangeCodeRepository).deleteByExpiresAtBefore(org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(exchangeCodeRepository).save(captor.capture());
        assertEquals(43, rawCode.length());
        assertEquals(64, captor.getValue().getCodeHash().length());
        assertNotEquals(rawCode, captor.getValue().getCodeHash());
    }

    @Test
    @DisplayName("유효한 교환 코드는 사용 처리 후 RenewMate JWT 응답으로 교환한다")
    void shouldExchangeCodeOnce() {
        User user = org.mockito.Mockito.mock(User.class);
        OAuthExchangeCode code = org.mockito.Mockito.mock(OAuthExchangeCode.class);
        LoginResponse response = new LoginResponse(1L, "사용자", "user@example.com", "access-token");
        when(exchangeCodeRepository.findByCodeHash(anyString())).thenReturn(Optional.of(code));
        when(code.isUsed()).thenReturn(false);
        when(code.isExpired(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(code.getUser()).thenReturn(user);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(authService.createLoginResponse(user)).thenReturn(response);

        LoginResponse actual = oauthLoginService.exchange("one-time-code");

        assertEquals(response, actual);
        verify(code).markUsed(org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    @DisplayName("이미 사용된 교환 코드는 거부한다")
    void shouldRejectUsedCode() {
        OAuthExchangeCode code = org.mockito.Mockito.mock(OAuthExchangeCode.class);
        when(exchangeCodeRepository.findByCodeHash(anyString())).thenReturn(Optional.of(code));
        when(code.isUsed()).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> oauthLoginService.exchange("used-code")
        );

        assertEquals(ErrorCode.INVALID_OAUTH_EXCHANGE_CODE, exception.getErrorCode());
    }

    @Test
    @DisplayName("만료된 교환 코드는 거부한다")
    void shouldRejectExpiredCode() {
        OAuthExchangeCode code = org.mockito.Mockito.mock(OAuthExchangeCode.class);
        when(exchangeCodeRepository.findByCodeHash(anyString())).thenReturn(Optional.of(code));
        when(code.isUsed()).thenReturn(false);
        when(code.isExpired(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> oauthLoginService.exchange("expired-code")
        );

        assertEquals(ErrorCode.INVALID_OAUTH_EXCHANGE_CODE, exception.getErrorCode());
    }
}
