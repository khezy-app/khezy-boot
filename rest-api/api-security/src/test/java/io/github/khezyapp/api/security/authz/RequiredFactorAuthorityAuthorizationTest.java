package io.github.khezyapp.api.security.authz;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthoritiesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RequiredFactorAuthorityAuthorizationTest {

    private static void stubAuthorities(final Authentication auth, final String... authorities) {
        when(auth.getAuthorities()).thenAnswer(
                inv -> java.util.stream.Stream.of(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void shouldGrantWhenAuthenticatedAndNoFactorsRequired() {
        final var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        stubAuthorities(auth);

        final var repo = mock(RequiredFactorAuthoritiesRepository.class);
        when(repo.findRequiredFactorAuthorities(auth.getName())).thenReturn(List.of());

        final var authz = new RequiredFactorAuthorityAuthorization<>(repo, List.of());
        final var result = authz.authorize(() -> auth, null);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();
    }

    @Test
    void shouldDenyWhenNotAuthenticated() {
        final var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        final var authz = new RequiredFactorAuthorityAuthorization<>(null, List.of());
        final var result = authz.authorize(() -> auth, null);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
    }

    @Test
    void shouldDenyWhenMissingRequiredFactors() {
        final var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user");
        stubAuthorities(auth, "ROLE_USER");

        final var repo = mock(RequiredFactorAuthoritiesRepository.class);
        when(repo.findRequiredFactorAuthorities("user"))
                .thenReturn(List.of("FACTOR_PASSWORD", "FACTOR_WEBAUTHN"));

        final var authz = new RequiredFactorAuthorityAuthorization<>(repo, List.of());
        final var result = (RequiredFactorAuthorityDecision) authz.authorize(() -> auth, null);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
        assertThat(result.getFactorErrors()).hasSize(2);
        assertThat(result.getFactorErrors()).allMatch(err -> Objects.nonNull(err) && err.isMissing());
    }

    @Test
    void shouldGrantWhenAllRequiredFactorsPresent() {
        final var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user");
        stubAuthorities(auth, "ROLE_USER", "FACTOR_PASSWORD", "FACTOR_WEBAUTHN");

        final var repo = mock(RequiredFactorAuthoritiesRepository.class);
        when(repo.findRequiredFactorAuthorities("user"))
                .thenReturn(List.of("FACTOR_PASSWORD", "FACTOR_WEBAUTHN"));

        final var authz = new RequiredFactorAuthorityAuthorization<>(repo, List.of());
        final var result = authz.authorize(() -> auth, null);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();
    }

    @Test
    void shouldIncludeGlobalMFAuthorities() {
        final var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user");
        stubAuthorities(auth, "ROLE_USER");

        final var repo = mock(RequiredFactorAuthoritiesRepository.class);
        when(repo.findRequiredFactorAuthorities("user")).thenReturn(List.of("FACTOR_PASSWORD"));

        final var authz = new RequiredFactorAuthorityAuthorization<>(repo, List.of("FACTOR_WEBAUTHN"));
        final var result = (RequiredFactorAuthorityDecision) authz.authorize(() -> auth, null);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
        assertThat(result.getFactorErrors()).hasSize(2);
    }

    @Test
    void shouldHandleNullRepository() {
        final var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user");
        stubAuthorities(auth, "ROLE_USER");

        final var authz = new RequiredFactorAuthorityAuthorization<>(null, List.of("FACTOR_PASSWORD"));
        final var result = (RequiredFactorAuthorityDecision) authz.authorize(() -> auth, null);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
        assertThat(result.getFactorErrors()).hasSize(1);
    }

    @Test
    void checkShouldReturnDecision() {
        final var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        final var authz = new RequiredFactorAuthorityAuthorization<>(null, List.of());
        final var result = authz.check(() -> auth, null);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
    }
}
