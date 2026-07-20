package io.github.khezyapp.api.security.token.factory;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import org.springframework.security.core.Authentication;

/**
 * Factory strategy for creating {@link AuthenticationBuilder} instances.
 * Implementations indicate which {@link Authentication} type they can handle
 * and produce the appropriate builder for that type.
 */
public interface AuthenticationBuilderFactory {

    /**
     * Returns {@code true} if this factory can create a builder for the given authentication class.
     *
     * @param authentication the authentication class to check
     * @return {@code true} if a builder can be created
     */
    boolean supports(Class<?> authentication);

    /**
     * Creates an {@link AuthenticationBuilder} for the given authentication token.
     *
     * @param authentication the existing authentication token
     * @return a new builder pre-populated from the token
     */
    AuthenticationBuilder<?> create(Authentication authentication);
}
