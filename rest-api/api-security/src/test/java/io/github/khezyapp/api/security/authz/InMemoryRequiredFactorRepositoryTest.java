package io.github.khezyapp.api.security.authz;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRequiredFactorRepositoryTest {

    @Test
    void shouldReturnRequiredFactors() {
        final var repo = new InMemoryRequiredFactorRepository(Map.of(
                "alice", List.of("FACTOR_PASSWORD", "FACTOR_WEBAUTHN")
        ));

        final var factors = repo.findRequiredFactorAuthorities("alice");

        assertThat(factors).containsExactly("FACTOR_PASSWORD", "FACTOR_WEBAUTHN");
    }

    @Test
    void shouldReturnEmptyForUnknownUser() {
        final var repo = new InMemoryRequiredFactorRepository(Map.of());

        final var factors = repo.findRequiredFactorAuthorities("unknown");

        assertThat(factors).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUserWithoutRequiredFactors() {
        final var repo = new InMemoryRequiredFactorRepository(Map.of(
                "alice", List.of()
        ));

        final var factors = repo.findRequiredFactorAuthorities("alice");

        assertThat(factors).isEmpty();
    }
}
