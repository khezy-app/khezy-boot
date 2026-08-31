package io.github.khezyapp.a2a.starter.security;

import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import io.github.khezyapp.a2a.core.identity.CallerIdentityResolver;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves the caller identity from Spring Security's {@link SecurityContextHolder}.
 * Registered by the starter only when Spring Security is on the classpath.
 */
public final class SecurityCallerIdentityResolver implements CallerIdentityResolver {

    @Override
    public CallerIdentity resolve() {
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (Objects.isNull(authentication)) {
            return CallerIdentity.anonymous();
        }
        final var subject = Optional.ofNullable(authentication.getName());
        final var scopes = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        final var claims = Map.<String, Object>of("principal", authentication.getPrincipal());
        return new CallerIdentity(subject, scopes, claims);
    }
}
