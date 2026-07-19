package io.github.khezyapp.api.security.authority;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredFactorErrorTest {

    @Test
    void createMissingShouldHaveCorrectFields() {
        final var error = RequiredFactorError.createMissing("FACTOR_X509");

        assertThat(error.isMissing()).isTrue();
        assertThat(error.getAuthority()).isEqualTo("FACTOR_X509");
    }

    @Test
    void toStringShouldContainAuthority() {
        final var error = RequiredFactorError.createMissing("FACTOR_PASSWORD");
        assertThat(error.toString()).contains("FACTOR_PASSWORD");
    }

    @Test
    void shouldThrowOnEmptyAuthority() {
        assertThatThrownBy(() -> RequiredFactorError.createMissing(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
