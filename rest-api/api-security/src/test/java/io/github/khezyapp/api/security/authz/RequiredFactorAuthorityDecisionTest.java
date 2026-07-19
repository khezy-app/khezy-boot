package io.github.khezyapp.api.security.authz;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredFactorAuthorityDecisionTest {

    @Test
    void shouldCarryFactorErrors() {
        final var decision = new RequiredFactorAuthorityDecision(false, List.of("FACTOR_PASSWORD", "FACTOR_WEBAUTHN"));

        assertThat(decision.isGranted()).isFalse();
        assertThat(decision.getFactorErrors()).hasSize(2);
        assertThat(decision.getFactorErrors()).allMatch(err -> err.isMissing());
        assertThat(decision.getFactorErrors()).extracting(err -> err.getAuthority())
                .containsExactly("FACTOR_PASSWORD", "FACTOR_WEBAUTHN");
    }

    @Test
    void shouldBeGrantedWhenNoMissingAuthorities() {
        final var decision = new RequiredFactorAuthorityDecision(true, List.of());

        assertThat(decision.isGranted()).isTrue();
        assertThat(decision.getFactorErrors()).isEmpty();
    }

    @Test
    void toStringShouldContainDetails() {
        final var decision = new RequiredFactorAuthorityDecision(false, List.of("FACTOR_PASSWORD"));
        final var str = decision.toString();
        assertThat(str).contains("RequiredFactorAuthorityDecision");
        assertThat(str).contains("granted=false");
        assertThat(str).contains("FACTOR_PASSWORD");
    }
}
