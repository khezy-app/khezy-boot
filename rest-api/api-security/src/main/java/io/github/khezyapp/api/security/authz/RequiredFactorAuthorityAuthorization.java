package io.github.khezyapp.api.security.authz;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthoritiesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * An {@link AuthorizationManager} that checks whether the current authentication
 * possesses all required multi-factor authorities. Combines per-user required factors
 * from the repository with global mandatory factors, then grants access only when
 * no authorities are missing.
 */
@RequiredArgsConstructor
public class RequiredFactorAuthorityAuthorization<T> implements AuthorizationManager<T> {
    private final AuthenticatedAuthorizationManager<T> authenticated =
            AuthenticatedAuthorizationManager.authenticated();
    private final RequiredFactorAuthoritiesRepository requiredFactorAuthoritiesRepository;
    private final List<String> globalMFAuthorities;

    /**
     * Casts the result of {@link #authorize(Supplier, Object)} to an
     * {@link AuthorizationDecision}.
     */
    @Override
    public AuthorizationDecision check(final Supplier<Authentication> authentication,
                                       final T object) {
        return (AuthorizationDecision) authorize(authentication, object);
    }

    /**
     * Verifies the user is authenticated, then checks that all required multi-factor
     * authorities (per-user and global) are present in the current grant.
     *
     * @return a {@link RequiredFactorAuthorityDecision} listing any missing authorities
     */
    @Override
    public AuthorizationResult authorize(final Supplier<Authentication> authentication,
                                         final T object) {
        final var authenticatedResult = authenticated.authorize(authentication, object);
        if (Objects.nonNull(authenticatedResult) && !authenticatedResult.isGranted()) {
            return authenticatedResult;
        }

        final var currentAuthentication = authentication.get();
        final var authorities = getGrantedAuthorities(currentAuthentication);

        final var missingAuthorities = Optional.ofNullable(getMFAuthorities(currentAuthentication))
                .map(ArrayList::new)
                .orElse(new ArrayList<>());

        if (Objects.nonNull(globalMFAuthorities) && !globalMFAuthorities.isEmpty()) {
            missingAuthorities.addAll(globalMFAuthorities);
        }

        missingAuthorities.removeIf(authorities::contains);

        return new RequiredFactorAuthorityDecision(missingAuthorities.isEmpty(), missingAuthorities);
    }

    private List<String> getMFAuthorities(final Authentication authentication) {
        return Optional.ofNullable(requiredFactorAuthoritiesRepository)
                .map(repo -> repo.findRequiredFactorAuthorities(authentication.getName()))
                .orElse(null);
    }

    private List<String> getGrantedAuthorities(final Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
