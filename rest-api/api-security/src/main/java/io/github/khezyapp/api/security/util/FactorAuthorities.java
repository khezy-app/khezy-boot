package io.github.khezyapp.api.security.util;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Utility methods for converting between factor authority strings
 * ({@code FACTOR_WEBAUTHN}) and human-readable factor method names
 * ({@code webauthn}).
 */
public final class FactorAuthorities {

    private FactorAuthorities() {
    }

    public static final String FACTOR_PREFIX = "FACTOR_";

    /**
     * Strips the {@value #FACTOR_PREFIX} prefix from a factor authority and
     * lower-cases the remainder. Returns the input unchanged if it has no text
     * content.
     *
     * @param factorAuthority e.g. {@code FACTOR_WEBAUTHN}
     * @return e.g. {@code webauthn}, or the original value if blank
     */
    public static String getFactorMethod(final String factorAuthority) {
        if (!StringUtils.hasText(factorAuthority)) {
            return factorAuthority;
        }
        return factorAuthority.replaceFirst(FACTOR_PREFIX, "").toLowerCase(Locale.ROOT);
    }

    /**
     * Prepends the {@value #FACTOR_PREFIX} prefix to a factor method name.
     * If the input already starts with {@code FACTOR_}, it is uppercased and
     * returned as-is.
     *
     * @param factorMethod e.g. {@code webauthn}
     * @return e.g. {@code FACTOR_WEBAUTHN}
     */
    public static String getFactorAuthorityFromMethod(final String factorMethod) {
        Assert.hasText(factorMethod, "factorMethod cannot be blank");
        if (factorMethod.toUpperCase(Locale.ROOT).startsWith(FACTOR_PREFIX)) {
            return factorMethod.toUpperCase(Locale.ROOT);
        }
        return FACTOR_PREFIX + factorMethod.toUpperCase(Locale.ROOT);
    }
}
