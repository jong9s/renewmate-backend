package com.renewmate.auth.google;

public record VerifiedGoogleIdentity(
        String subject,
        String email,
        String name,
        boolean emailVerified
) {
}
