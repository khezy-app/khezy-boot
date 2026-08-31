package io.github.khezyapp.a2a.starter.security;

import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityCallerIdentityResolverTest {

    private final SecurityCallerIdentityResolver resolver = new SecurityCallerIdentityResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Named authentication resolves subject, scopes and principal claim")
    void shouldResolveNamedAuthentication() {
        final var authentication = new TestAuthentication(List.of(
                new SimpleGrantedAuthority("SCOPE_agent:run"),
                new SimpleGrantedAuthority("SCOPE_tasks:read")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        final var identity = resolver.resolve();

        assertThat(identity.subject()).contains("user");
        assertThat(identity.scopes()).containsExactlyInAnyOrder("SCOPE_agent:run", "SCOPE_tasks:read");
        assertThat(identity.claims()).containsEntry("principal", "user");
    }

    @Test
    @DisplayName("Missing authentication resolves to anonymous identity")
    void shouldResolveAnonymousWithoutAuthentication() {
        assertThat(resolver.resolve()).isEqualTo(CallerIdentity.anonymous());
    }

    /** Minimal token whose name derives from a string principal. */
    private static final class TestAuthentication extends AbstractAuthenticationToken {

        private TestAuthentication(final List<GrantedAuthority> authorities) {
            super(authorities);
        }

        @Override
        public Object getPrincipal() {
            return "user";
        }

        @Override
        public Object getCredentials() {
            return "";
        }
    }
}
