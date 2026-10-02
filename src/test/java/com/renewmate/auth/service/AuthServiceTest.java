package com.renewmate.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.renewmate.auth.dto.LoginRequest;
import com.renewmate.auth.dto.LoginResponse;
import com.renewmate.auth.dto.SignupRequest;
import com.renewmate.auth.google.VerifiedGoogleIdentity;
import com.renewmate.auth.service.AuthService;
import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.global.security.JwtProvider;
import com.renewmate.user.entity.User;
import com.renewmate.user.entity.UserStatus;
import com.renewmate.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("회원가입 시 비밀번호를 암호화하여 사용자를 저장한다")
    void shouldSignupWithEncodedPassword() {

        // Given
        SignupRequest request = new SignupRequest(
                "홍길동",
                "test@example.com",
                "password123",
                "password123"
        );

        when(userRepository.existsByEmail(request.email()))
                .thenReturn(false);

        when(passwordEncoder.encode(request.password()))
                .thenReturn("encoded-password");

        // When
        authService.signup(request);

        // Then
        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals("홍길동", savedUser.getName());
        assertEquals("test@example.com", savedUser.getEmail());
        assertEquals("encoded-password", savedUser.getPassword());

        verify(passwordEncoder).encode("password123");
    }

    @Test
    @DisplayName("이미 가입된 이메일로 회원가입할 수 없다")
    void shouldRejectDuplicateEmail() {

        // Given
        SignupRequest request = new SignupRequest(
                "홍길동",
                "test@example.com",
                "password123",
                "password123"
        );

        when(userRepository.existsByEmail(request.email()))
                .thenReturn(true);

        // When
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.signup(request)
        );

        // Then
        assertEquals(
                ErrorCode.DUPLICATE_EMAIL,
                exception.getErrorCode()
        );

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("비밀번호 확인이 일치하지 않으면 회원가입할 수 없다")
    void shouldRejectPasswordMismatch() {

        // Given
        SignupRequest request = new SignupRequest(
                "홍길동",
                "test@example.com",
                "password123",
                "different123"
        );

        when(userRepository.existsByEmail(request.email()))
                .thenReturn(false);

        // When
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.signup(request)
        );

        // Then
        assertEquals(
                ErrorCode.PASSWORD_MISMATCH,
                exception.getErrorCode()
        );

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("로그인에 성공하면 JWT를 발급한다")
    void shouldLoginAndIssueJwt() {

        // Given
        LoginRequest request = new LoginRequest(
                "test@example.com",
                "password123"
        );

        User user = org.mockito.Mockito.mock(User.class);

        when(userRepository.findByEmailIgnoreCase(request.email()))
                .thenReturn(Optional.of(user));

        when(user.getPassword()).thenReturn("encoded-password");
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(passwordEncoder.matches(
                "password123",
                "encoded-password"
        )).thenReturn(true);

        when(user.getUserId()).thenReturn(1L);
        when(user.getName()).thenReturn("홍길동");
        when(user.getEmail()).thenReturn("test@example.com");

        when(jwtProvider.createAccessToken(
                1L,
                "test@example.com"
        )).thenReturn("test-access-token");

        // When
        LoginResponse response = authService.login(request);

        // Then
        assertNotNull(response);
        assertEquals(1L, response.userId());
        assertEquals("홍길동", response.name());
        assertEquals("test@example.com", response.email());
        assertEquals("test-access-token", response.accessToken());

        verify(jwtProvider).createAccessToken(
                1L,
                "test@example.com"
        );
    }

    @Test
    @DisplayName("비밀번호가 틀리면 로그인할 수 없다")
    void shouldRejectInvalidPassword() {

        // Given
        LoginRequest request = new LoginRequest(
                "test@example.com",
                "wrong-password"
        );

        User user = org.mockito.Mockito.mock(User.class);

        when(userRepository.findByEmailIgnoreCase(request.email()))
                .thenReturn(Optional.of(user));

        when(user.getPassword()).thenReturn("encoded-password");
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);

        when(passwordEncoder.matches(
                "wrong-password",
                "encoded-password"
        )).thenReturn(false);

        // When
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(request)
        );

        // Then
        assertEquals(
                ErrorCode.INVALID_LOGIN,
                exception.getErrorCode()
        );

        verify(jwtProvider, never())
                .createAccessToken(any(), any());
    }

    @Test
    @DisplayName("Google 로그인 시 기존 이메일 계정에 Google 식별자를 연결한다")
    void shouldLinkGoogleIdentityToExistingUser() {
        VerifiedGoogleIdentity identity = new VerifiedGoogleIdentity(
                "google-subject",
                "test@example.com",
                "홍길동",
                true
        );
        User user = org.mockito.Mockito.mock(User.class);

        when(userRepository.findByGoogleSubject(identity.subject())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(identity.email())).thenReturn(Optional.of(user));
        when(user.getGoogleSubject()).thenReturn(null);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        User resolvedUser = authService.resolveGoogleUser(identity);

        verify(user).linkGoogleSubject("google-subject");
        assertEquals(user, resolvedUser);
    }

    @Test
    @DisplayName("Google 로그인 시 계정이 없으면 신규 사용자를 생성한다")
    void shouldCreateUserForNewGoogleIdentity() {
        VerifiedGoogleIdentity identity = new VerifiedGoogleIdentity(
                "new-google-subject",
                "new@example.com",
                "새 사용자",
                true
        );

        when(userRepository.findByGoogleSubject(identity.subject())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(identity.email())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded-random-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User resolvedUser = authService.resolveGoogleUser(identity);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("new-google-subject", userCaptor.getValue().getGoogleSubject());
        assertEquals("new@example.com", userCaptor.getValue().getEmail());
        assertEquals(userCaptor.getValue(), resolvedUser);
    }
}
