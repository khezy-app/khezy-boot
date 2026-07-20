package io.github.khezyapp.api.security.token.factory;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import io.github.khezyapp.api.security.token.builder.OAuth2LonginAuthenticationBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;

/**
 * Factory for {@link io.github.khezyapp.api.security.token.builder.OAuth2LonginAuthenticationBuilder}.
 * Supports {@link OAuth2LoginAuthenticationToken}.
 */
public class OAuth2LonginAuthenticationBuilderFactory implements AuthenticationBuilderFactory {

    @Override
    public boolean supports(final Class<?> authentication) {
        return OAuth2LoginAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public AuthenticationBuilder<?> create(final Authentication authentication) {
        return new OAuth2LonginAuthenticationBuilder<>((OAuth2LoginAuthenticationToken) authentication);
    }


}
