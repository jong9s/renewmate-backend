package com.renewmate.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import com.renewmate.auth.service.OAuthLoginService;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthSuccessHandlerTest {

    @Mock
    private OAuthLoginService oauthLoginService;

    @InjectMocks
    private GoogleOAuthSuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(successHandler, "frontendBaseUrl", "http://localhost:5173");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("OAuth 성공 시 JWT 대신 일회용 코드만 프론트 콜백으로 전달한다")
    void shouldRedirectWithExchangeCodeAndClearSession() throws Exception {
        OidcUser oidcUser = org.mockito.Mockito.mock(OidcUser.class);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(oidcUser, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(oauthLoginService.issueExchangeCode(oidcUser)).thenReturn("one-time-code");

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertEquals(
                "http://localhost:5173/auth/google/callback#code=one-time-code",
                response.getRedirectedUrl()
        );
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        assertNull(request.getSession(false));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("OAuth 후처리 실패 시 내부 오류 없이 로그인 화면으로 복귀한다")
    void shouldRedirectToLoginWhenPostAuthenticationFails() throws Exception {
        OidcUser oidcUser = org.mockito.Mockito.mock(OidcUser.class);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(oidcUser, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(oauthLoginService.issueExchangeCode(oidcUser)).thenThrow(new IllegalStateException("failure"));

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("http://localhost:5173/login?oauthError=google", response.getRedirectedUrl());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }
}
