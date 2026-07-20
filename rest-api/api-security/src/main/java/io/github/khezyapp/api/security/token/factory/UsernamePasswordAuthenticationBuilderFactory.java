package io.github.khezyapp.api.security.token.factory;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import io.github.khezyapp.api.security.token.builder.UsernamePasswordAuthenticationBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Factory for {@link io.github.khezyapp.api.security.token.builder.UsernamePasswordAuthenticationBuilder}.
 * Supports {@link UsernamePasswordAuthenticationToken}.
 */
public class UsernamePasswordAuthenticationBuilderFactory implements AuthenticationBuilderFactory {

    @Override
    public boolean supports(final Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public AuthenticationBuilder<?> create(final Authentication authentication) {
        return new UsernamePasswordAuthenticationBuilder<>((UsernamePasswordAuthenticationToken) authentication);
    }
}
