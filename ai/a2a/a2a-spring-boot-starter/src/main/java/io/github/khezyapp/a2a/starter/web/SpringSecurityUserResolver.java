package io.github.khezyapp.a2a.starter.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import jakarta.servlet.http.HttpServletRequest;
import org.a2aproject.sdk.server.auth.AuthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Maps the Spring Security context onto the SDK {@link User} contract.
 *
 * <p>Anonymous and unauthenticated authentications resolve to an empty optional, which the
 * call-context factory turns into {@code UnauthenticatedUser}. Authorities are exposed as
 * user attributes so agents can make authorization decisions without touching Spring
 * Security types.</p>
 */
public class SpringSecurityUserResolver implements Function<HttpServletRequest, Optional<User>> {

    /**
     * Creates the resolver.
     */
    public SpringSecurityUserResolver() {
    }

    @Override
    public Optional<User> apply(final HttpServletRequest request) {
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedUser(authentication.getName(), attributes(authentication)));
    }

    private static Map<String, Object> attributes(final Authentication authentication) {
        final var attributes = new LinkedHashMap<String, Object>();
        attributes.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());
        final var details = authentication.getDetails();
        if (details != null) {
            attributes.put("details", details);
        }
        return Map.copyOf(attributes);
    }
}
