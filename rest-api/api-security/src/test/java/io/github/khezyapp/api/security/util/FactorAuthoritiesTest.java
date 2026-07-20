package io.github.khezyapp.api.security.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FactorAuthoritiesTest {

    @Test
    void getFactorMethodShouldStripPrefixAndLowercase() {
        assertThat(FactorAuthorities.getFactorMethod("FACTOR_PASSWORD")).isEqualTo("password");
        assertThat(FactorAuthorities.getFactorMethod("FACTOR_WEBAUTHN")).isEqualTo("webauthn");
    }

    @Test
    void getFactorMethodShouldHandleNull() {
        assertThat(FactorAuthorities.getFactorMethod(null)).isNull();
    }

    @Test
    void getFactorMethodShouldHandleEmpty() {
        assertThat(FactorAuthorities.getFactorMethod("")).isEqualTo("");
    }

    @Test
    void getFactorMethodShouldHandleNonFactorString() {
        assertThat(FactorAuthorities.getFactorMethod("SOME_PREFIX_VALUE")).isEqualTo("some_prefix_value");
    }

    @Test
    void getFactorAuthorityFromMethodShouldAddPrefix() {
        assertThat(FactorAuthorities.getFactorAuthorityFromMethod("password"))
                .isEqualTo("FACTOR_PASSWORD");
    }

    @Test
    void getFactorAuthorityFromMethodShouldUppercase() {
        assertThat(FactorAuthorities.getFactorAuthorityFromMethod("webauthn"))
                .isEqualTo("FACTOR_WEBAUTHN");
    }

    @Test
    void getFactorAuthorityFromMethodShouldNotDuplicatePrefix() {
        assertThat(FactorAuthorities.getFactorAuthorityFromMethod("FACTOR_PASSWORD"))
                .isEqualTo("FACTOR_PASSWORD");
    }

    @Test
    void getFactorAuthorityFromMethodShouldHandleMixedCasePrefix() {
        assertThat(FactorAuthorities.getFactorAuthorityFromMethod("factor_password"))
                .isEqualTo("FACTOR_PASSWORD");
    }

    @Test
    void getFactorAuthorityFromMethodShouldThrowOnEmpty() {
        assertThatThrownBy(() -> FactorAuthorities.getFactorAuthorityFromMethod(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getFactorAuthorityFromMethodShouldThrowOnBlank() {
        assertThatThrownBy(() -> FactorAuthorities.getFactorAuthorityFromMethod(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundtripShouldWork() {
        final var original = "password";
        final var authority = FactorAuthorities.getFactorAuthorityFromMethod(original);
        final var back = FactorAuthorities.getFactorMethod(authority);
        assertThat(back).isEqualTo(original);
    }
}
