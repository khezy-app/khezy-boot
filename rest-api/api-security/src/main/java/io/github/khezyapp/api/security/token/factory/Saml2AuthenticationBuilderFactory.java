package io.github.khezyapp.api.security.token.factory;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import io.github.khezyapp.api.security.token.builder.Saml2AuthenticationBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;

/**
 * Factory for {@link io.github.khezyapp.api.security.token.builder.Saml2AuthenticationBuilder}.
 * Supports {@link Saml2Authentication}.
 */
public class Saml2AuthenticationBuilderFactory implements AuthenticationBuilderFactory {

    @Override
    public boolean supports(final Class<?> authentication) {
        return Saml2Authentication.class.isAssignableFrom(authentication);
    }

    @Override
    public AuthenticationBuilder<?> create(final Authentication authentication) {
        return new Saml2AuthenticationBuilder<>((Saml2Authentication) authentication);
    }
}
