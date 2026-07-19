package io.github.khezyapp.api.security.token.factory;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import io.github.khezyapp.api.security.token.builder.CASTokenAuthenticationBuilder;
import org.springframework.security.cas.authentication.CasAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Factory for {@link io.github.khezyapp.api.security.token.builder.CASTokenAuthenticationBuilder}.
 * Supports {@link CasAuthenticationToken}.
 */
public class CASTokenAuthenticationBuilderFactory implements AuthenticationBuilderFactory {

    @Override
    public boolean supports(final Class<?> authentication) {
        return CasAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public AuthenticationBuilder<?> create(final Authentication authentication) {
        return new CASTokenAuthenticationBuilder<>((CasAuthenticationToken) authentication);
    }

}
