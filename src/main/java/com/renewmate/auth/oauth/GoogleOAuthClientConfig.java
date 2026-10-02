package com.renewmate.auth.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.Assert;

@Configuration
public class GoogleOAuthClientConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.google.oauth.client-id:}") String clientId,
            @Value("${app.google.oauth.client-secret:}") String clientSecret
    ) {
        Assert.hasText(clientId, "GOOGLE_CLIENT_ID must be configured");
        Assert.hasText(clientSecret, "GOOGLE_CLIENT_SECRET must be configured");

        ClientRegistration google = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("openid", "profile", "email")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();

        return new InMemoryClientRegistrationRepository(google);
    }
}
