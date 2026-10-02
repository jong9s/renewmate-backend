package com.renewmate.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.auth.dto.LoginResponse;
import com.renewmate.auth.entity.OAuthExchangeCode;
import com.renewmate.auth.google.VerifiedGoogleIdentity;
import com.renewmate.auth.repository.OAuthExchangeCodeRepository;
import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.user.entity.User;
import com.renewmate.user.entity.UserStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OAuthExchangeCodeRepository exchangeCodeRepository;
    private final AuthService authService;

    @Value("${app.oauth.exchange-code-expiration-seconds:60}")
    private long expirationSeconds;

    @Transactional
    public String issueExchangeCode(OidcUser oidcUser) {
        VerifiedGoogleIdentity identity = new VerifiedGoogleIdentity(
                oidcUser.getSubject(),
                oidcUser.getEmail() == null ? null : oidcUser.getEmail().toLowerCase(),
                oidcUser.getFullName(),
                Boolean.TRUE.equals(oidcUser.getEmailVerified())
        );

        if (identity.subject() == null || identity.email() == null || !identity.emailVerified()) {
            throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }

        User user = authService.resolveGoogleUser(identity);
        exchangeCodeRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        exchangeCodeRepository.deleteAllByUser_UserId(user.getUserId());

        String rawCode = createRawCode();
        exchangeCodeRepository.save(OAuthExchangeCode.create(
                user,
                hash(rawCode),
                LocalDateTime.now().plusSeconds(expirationSeconds)
        ));
        return rawCode;
    }

    @Transactional
    public LoginResponse exchange(String rawCode) {
        OAuthExchangeCode code = exchangeCodeRepository.findByCodeHash(hash(rawCode))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OAUTH_EXCHANGE_CODE));

        LocalDateTime now = LocalDateTime.now();
        if (code.isUsed() || code.isExpired(now) || code.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH_EXCHANGE_CODE);
        }

        code.markUsed(now);
        return authService.createLoginResponse(code.getUser());
    }

    private String createRawCode() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
