package io.github.khezyapp.api.security.token.factory;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import io.github.khezyapp.api.security.token.builder.X509AuthenticationBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Factory for {@link io.github.khezyapp.api.security.token.builder.X509AuthenticationBuilder}.
 * Supports {@link PreAuthenticatedAuthenticationToken}.
 */
public class X509AuthenticationBuilderFactory implements AuthenticationBuilderFactory {

    @Override
    public boolean supports(final Class<?> authentication) {
        return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public AuthenticationBuilder<?> create(final Authentication authentication) {
        return new X509AuthenticationBuilder<>((PreAuthenticatedAuthenticationToken) authentication);
    }
}
