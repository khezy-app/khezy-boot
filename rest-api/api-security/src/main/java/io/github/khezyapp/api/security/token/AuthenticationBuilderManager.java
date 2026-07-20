package io.github.khezyapp.api.security.token;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import io.github.khezyapp.api.security.token.factory.AuthenticationBuilderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Delegates authentication token transformation to the appropriate
 * {@link AuthenticationBuilderFactory} based on the token type.
 * Iterates through registered factories and applies a {@link Consumer} to the matching builder.
 */
@RequiredArgsConstructor
public class AuthenticationBuilderManager {
    private final List<AuthenticationBuilderFactory> factories;

    /**
     * Finds a matching factory for the given authentication token, applies the builder consumer
     * to modify it, and returns the rebuilt token. If no factory matches, returns the original token unchanged.
     *
     * @param authentication the existing authentication token to transform
     * @param builder        a consumer that modifies the {@link AuthenticationBuilder} before building
     * @return a new (or unchanged) authentication token
     */
    public Authentication build(final Authentication authentication,
                                final Consumer<AuthenticationBuilder<?>> builder) {
        for (final var factoryBuilder : factories) {
            if (Objects.nonNull(authentication) && factoryBuilder.supports(authentication.getClass())) {
                final var authenticationBuilder = factoryBuilder.create(authentication);
                builder.accept(authenticationBuilder);
                return authenticationBuilder.build();
            }
        }
        return authentication;
    }

    public List<AuthenticationBuilderFactory> getFactories() {
        return List.copyOf(factories);
    }
}
