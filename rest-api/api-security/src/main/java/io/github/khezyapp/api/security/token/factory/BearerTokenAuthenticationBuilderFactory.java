package io.github.khezyapp.api.security.token.factory;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import io.github.khezyapp.api.security.token.builder.BearerTokenAuthenticationBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;

/**
 * Factory for {@link io.github.khezyapp.api.security.token.builder.BearerTokenAuthenticationBuilder}.
 * Supports {@link BearerTokenAuthentication}.
 */
public class BearerTokenAuthenticationBuilderFactory implements AuthenticationBuilderFactory {

    @Override
    public boolean supports(final Class<?> authentication) {
        return BearerTokenAuthentication.class.isAssignableFrom(authentication);
    }

    @Override
    public AuthenticationBuilder<?> create(final Authentication authentication) {
        return new BearerTokenAuthenticationBuilder<>((BearerTokenAuthentication) authentication);
    }
}
