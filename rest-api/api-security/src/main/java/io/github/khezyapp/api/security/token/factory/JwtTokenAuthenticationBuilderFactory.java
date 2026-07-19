package io.github.khezyapp.api.security.token.factory;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import io.github.khezyapp.api.security.token.builder.JwtTokenAuthenticationBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Factory for {@link io.github.khezyapp.api.security.token.builder.JwtTokenAuthenticationBuilder}.
 * Supports {@link JwtAuthenticationToken}.
 */
public class JwtTokenAuthenticationBuilderFactory implements AuthenticationBuilderFactory {

    @Override
    public boolean supports(final Class<?> authentication) {
        return JwtAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public AuthenticationBuilder<?> create(final Authentication authentication) {
        return new JwtTokenAuthenticationBuilder<>((JwtAuthenticationToken) authentication);
    }

}
