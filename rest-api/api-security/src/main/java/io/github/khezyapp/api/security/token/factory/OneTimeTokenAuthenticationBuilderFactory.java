package io.github.khezyapp.api.security.token.factory;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import io.github.khezyapp.api.security.token.builder.OneTimeTokenAuthenticationBuilder;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Factory for {@link io.github.khezyapp.api.security.token.builder.OneTimeTokenAuthenticationBuilder}.
 * Supports {@link OneTimeTokenAuthenticationToken}.
 */
public class OneTimeTokenAuthenticationBuilderFactory implements AuthenticationBuilderFactory {

    @Override
    public boolean supports(final Class<?> authentication) {
        return OneTimeTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public AuthenticationBuilder<?> create(final Authentication authentication) {
        return new OneTimeTokenAuthenticationBuilder<>((OneTimeTokenAuthenticationToken) authentication);
    }
}
