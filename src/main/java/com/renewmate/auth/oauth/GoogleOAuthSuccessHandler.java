package com.renewmate.auth.oauth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.renewmate.auth.service.OAuthLoginService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthSuccessHandler.class);

    private final OAuthLoginService oauthLoginService;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try {
            if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
                redirectFailure(request, response);
                return;
            }

            String exchangeCode = oauthLoginService.issueExchangeCode(oidcUser);
            clearTransientSession(request);
            SecurityContextHolder.clearContext();

            String redirectUrl = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                    .path("/auth/google/callback")
                    .build()
                    .encode()
                    .toUriString()
                    + "#code=" + exchangeCode;

            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Referrer-Policy", "no-referrer");
            response.sendRedirect(redirectUrl);
        } catch (RuntimeException exception) {
            log.error("Google OAuth post-authentication handling failed", exception);
            redirectFailure(request, response);
        }
    }

    private void redirectFailure(HttpServletRequest request, HttpServletResponse response) throws IOException {
        clearTransientSession(request);
        SecurityContextHolder.clearContext();
        response.setHeader("Cache-Control", "no-store");
        response.sendRedirect(frontendBaseUrl + "/login?oauthError=google");
    }

    private void clearTransientSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
