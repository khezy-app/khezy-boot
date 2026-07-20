package io.github.khezyapp.api.security.authority;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredFactorAuthorityTest {

    @Test
    void fromAuthorityShouldCreateWithGivenString() {
        final var authority = RequiredFactorAuthority.fromAuthority("FACTOR_TEST");
        assertThat(authority.getAuthority()).isEqualTo("FACTOR_TEST");
    }

    @Test
    void fromFactorShouldNotPrependPrefix() {
        final var authority = RequiredFactorAuthority.fromFactor("PASSWORD");
        assertThat(authority.getAuthority()).isEqualTo("PASSWORD");
    }

    @Test
    void fromFactorShouldThrowWhenAlreadyPrefixed() {
        assertThatThrownBy(() -> RequiredFactorAuthority.fromFactor("FACTOR_X"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constantsShouldStartWithFactorPrefix() {
        assertThat(RequiredFactorAuthority.AUTHORIZATION_CODE_AUTHORITY).startsWith(RequiredFactorAuthority.PREFIX);
        assertThat(RequiredFactorAuthority.BEARER_AUTHORITY).startsWith(RequiredFactorAuthority.PREFIX);
        assertThat(RequiredFactorAuthority.CAS_AUTHORITY).startsWith(RequiredFactorAuthority.PREFIX);
        assertThat(RequiredFactorAuthority.OTT_AUTHORITY).startsWith(RequiredFactorAuthority.PREFIX);
        assertThat(RequiredFactorAuthority.PASSWORD_AUTHORITY).startsWith(RequiredFactorAuthority.PREFIX);
        assertThat(RequiredFactorAuthority.SAML_RESPONSE_AUTHORITY).startsWith(RequiredFactorAuthority.PREFIX);
        assertThat(RequiredFactorAuthority.WEBAUTHN_AUTHORITY).startsWith(RequiredFactorAuthority.PREFIX);
        assertThat(RequiredFactorAuthority.X509_AUTHORITY).startsWith(RequiredFactorAuthority.PREFIX);
    }

    @Test
    void equalsShouldWork() {
        final var a1 = RequiredFactorAuthority.fromAuthority("FACTOR_PASSWORD");
        final var a2 = RequiredFactorAuthority.fromAuthority("FACTOR_PASSWORD");
        final var a3 = RequiredFactorAuthority.fromAuthority("FACTOR_OTHER");

        assertThat(a1).isEqualTo(a2);
        assertThat(a1).isNotEqualTo(a3);
        assertThat(a1).isNotEqualTo(null);
        assertThat(a1).isNotEqualTo("some-string");
    }

    @Test
    void hashCodeShouldWork() {
        final var a1 = RequiredFactorAuthority.fromAuthority("FACTOR_PASSWORD");
        final var a2 = RequiredFactorAuthority.fromAuthority("FACTOR_PASSWORD");

        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }

    @Test
    void toStringShouldContainAuthority() {
        final var authority = RequiredFactorAuthority.fromAuthority("FACTOR_TEST");
        assertThat(authority.toString()).contains("FACTOR_TEST");
    }

    @Test
    void withAuthorityShouldThrowOnEmpty() {
        assertThatThrownBy(() -> RequiredFactorAuthority.withAuthority(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withFactorShouldThrowOnEmpty() {
        assertThatThrownBy(() -> RequiredFactorAuthority.withFactor(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
