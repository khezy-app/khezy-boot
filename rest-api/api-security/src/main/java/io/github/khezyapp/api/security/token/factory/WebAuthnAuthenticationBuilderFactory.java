package io.github.khezyapp.api.security.token.factory;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import io.github.khezyapp.api.security.token.builder.WebAuthnAuthenticationBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

/**
 * Factory for {@link io.github.khezyapp.api.security.token.builder.WebAuthnAuthenticationBuilder}.
 * Supports {@link WebAuthnAuthentication}.
 */
public class WebAuthnAuthenticationBuilderFactory implements AuthenticationBuilderFactory {

    @Override
    public boolean supports(final Class<?> authentication) {
        return WebAuthnAuthentication.class.isAssignableFrom(authentication);
    }

    @Override
    public AuthenticationBuilder<?> create(final Authentication authentication) {
        return new WebAuthnAuthenticationBuilder<>((WebAuthnAuthentication) authentication);
    }
}
