package io.github.khezyapp.api.security.authority;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;

import java.io.Serial;
import java.util.Objects;

/**
 * A {@link GrantedAuthority} representing a specific multi-factor authentication
 * method (e.g. {@code FACTOR_WEBAUTHN}, {@code FACTOR_OTT}). Use the static
 * factory methods to create instances from either a raw authority string or a
 * factor name without the prefix.
 */
public class RequiredFactorAuthority implements GrantedAuthority {
    public static final String PREFIX = "FACTOR_";

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String AUTHORIZATION_CODE_AUTHORITY = "FACTOR_AUTHORIZATION_CODE";

    public static final String BEARER_AUTHORITY = "FACTOR_BEARER";

    public static final String CAS_AUTHORITY = "FACTOR_CAS";

    public static final String OTT_AUTHORITY = "FACTOR_OTT";

    public static final String PASSWORD_AUTHORITY = "FACTOR_PASSWORD";

    public static final String SAML_RESPONSE_AUTHORITY = "FACTOR_SAML_RESPONSE";

    public static final String WEBAUTHN_AUTHORITY = "FACTOR_WEBAUTHN";

    public static final String X509_AUTHORITY = "FACTOR_X509";

    private final String authority;

    private RequiredFactorAuthority(final String authority) {
        this.authority = authority;
    }

    /**
     * Returns the full authority string, e.g. {@code FACTOR_WEBAUTHN}.
     */
    @Override
    public String getAuthority() {
        return authority;
    }

    /**
     * Returns a string representation of this authority.
     *
     * @return authority in the form {@code RequiredFactorAuthority [authority=FACTOR_...]}
     */
    @Override
    public String toString() {
        return "RequiredFactorAuthority [authority=" + authority + "]";
    }

    /**
     * Compares authorities by their string value.
     */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof RequiredFactorAuthority that)) {
            return false;
        }
        return Objects.equals(authority, that.authority);
    }

    /**
     * Hash code derived from the authority string.
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(authority);
    }

    /**
     * Creates a {@link Builder} for an authority string that already includes
     * the {@code FACTOR_} prefix (e.g. {@code FACTOR_WEBAUTHN}).
     */
    public static Builder withAuthority(final String authority) {
        return new Builder(authority);
    }

    /**
     * Creates a {@link Builder} for a factor name <em>without</em> the
     * {@code FACTOR_} prefix (e.g. {@code webauthn}). The prefix is added
     * automatically. Validates that the input does not already start with
     * {@code FACTOR_}.
     */
    public static Builder withFactor(final String factor) {
        Assert.hasText(factor, "factor cannot be empty");
        Assert.isTrue(!factor.startsWith(PREFIX), () -> "factor cannot start with 'FACTOR_' got: '" + factor + "'");
        return new Builder(factor);
    }

    /**
     * Shortcut for {@code withAuthority(authority).build()}.
     */
    public static RequiredFactorAuthority fromAuthority(final String authority) {
        return withAuthority(authority).build();
    }

    /**
     * Shortcut for {@code withFactor(factor).build()}.
     */
    public static RequiredFactorAuthority fromFactor(final String factor) {
        return withFactor(factor).build();
    }

    /**
     * Builder for creating a {@link RequiredFactorAuthority} with a validated
     * authority string.
     */
    public static final class Builder {
        private final String authority;

        private Builder(final String authority) {
            Assert.hasText(authority, "authority cannot be empty");
            this.authority = authority;
        }

        /**
         * Builds the {@link RequiredFactorAuthority} instance.
         *
         * @return a new authority with the configured string
         */
        public RequiredFactorAuthority build() {
            return new RequiredFactorAuthority(this.authority);
        }
    }
}
