package io.github.khezyapp.api.security.authn;

import io.github.khezyapp.api.security.token.AuthenticationBuilderManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Optional;

/**
 * An {@link AuthenticationManager} decorator that, after the delegate
 * authenticates, appends the current security context's authorities and
 * a factor authority to the result. Ensures multi-factor authorities
 * from prior authentication steps are preserved.
 */
@RequiredArgsConstructor
public class FactorAppendingAuthenticationManager implements AuthenticationManager {
    private final AuthenticationManager delegate;
    private final AuthenticationBuilderManager authenticationBuilderManager;

    /**
     * Delegates to the wrapped manager, then rebuilds the result with
     * authorities from the current security context and an appended factor
     * authority via {@link AuthenticationBuilderManager}.
     */
    @Override
    public Authentication authenticate(final Authentication authentication) throws AuthenticationException {
        final var authenticationResult = delegate.authenticate(authentication);
        final var currentAuthentication = SecurityContextHolder.getContext().getAuthentication();
        final var currentAuthorities = Optional.ofNullable(currentAuthentication)
                .map(Authentication::getAuthorities)
                .orElseGet(ArrayList::new);
        return authenticationBuilderManager.build(
                authenticationResult,
                builder -> builder
                        .authorities(currentAuthorities)
                        .addFactorAuthority()
        );
    }

}
